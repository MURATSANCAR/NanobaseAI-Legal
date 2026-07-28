package com.nanobase.specai.workflow.application;

import com.nanobase.specai.workflow.application.WorkflowModels.SimulationFinding;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowGraphNode;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowGraphTransition;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowSimulationInput;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowSimulationResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkflowSimulationService {
    private final WorkflowConditionEngine conditions;

    public WorkflowSimulationService(WorkflowConditionEngine conditions) {
        this.conditions = conditions;
    }

    public WorkflowSimulationResult simulate(WorkflowSimulationInput input) {
        List<SimulationFinding> findings = new ArrayList<>();
        Map<UUID, WorkflowGraphNode> nodes = new HashMap<>();
        input.nodes().forEach(node -> nodes.put(node.id(), node));
        Map<UUID, List<WorkflowGraphTransition>> outgoing = new HashMap<>();
        input.transitions().forEach(transition ->
            outgoing.computeIfAbsent(transition.sourceNodeId(), ignored -> new ArrayList<>())
                .add(transition));
        List<WorkflowGraphNode> entries = input.nodes().stream()
            .filter(node -> node.configuration().path("entry").asBoolean(false)).toList();
        if (entries.size() != 1) {
            findings.add(new SimulationFinding("ENTRY_COUNT", "ERROR", null,
                "Workflow must declare exactly one entry node"));
        }
        for (WorkflowGraphTransition transition : input.transitions()) {
            if (!nodes.containsKey(transition.sourceNodeId())
                || !nodes.containsKey(transition.targetNodeId())) {
                findings.add(new SimulationFinding("BROKEN_TRANSITION", "ERROR", null,
                    "Transition references a missing node"));
            }
        }
        for (WorkflowGraphNode node : input.nodes()) {
            if (!input.supportedNodeTypes().isEmpty()
                && !input.supportedNodeTypes().contains(node.typeConceptCode())) {
                findings.add(new SimulationFinding("UNSUPPORTED_NODE", "ERROR", node.code(),
                    "No installed provider supports this node concept"));
            }
            if (!input.authorizedNodeCodes().isEmpty()
                && !input.authorizedNodeCodes().contains(node.code())) {
                findings.add(new SimulationFinding("UNAUTHORIZED_NODE", "ERROR", node.code(),
                    "Simulation actor cannot execute this node"));
            }
            if (!node.configuration().path("terminal").asBoolean(false)
                && outgoing.getOrDefault(node.id(), List.of()).isEmpty()) {
                findings.add(new SimulationFinding("DEAD_END", "ERROR", node.code(),
                    "Non-terminal node has no outgoing transition"));
            }
        }

        List<String> visited = new ArrayList<>();
        Set<UUID> reachable = new HashSet<>();
        if (entries.size() == 1) {
            walk(entries.getFirst(), nodes, outgoing, input, visited, reachable, findings);
        }
        for (WorkflowGraphNode node : input.nodes()) {
            if (!reachable.contains(node.id())) {
                findings.add(new SimulationFinding("UNREACHABLE_NODE", "WARNING", node.code(),
                    "Node is not reachable for the supplied simulation data"));
            }
        }
        boolean reachedTerminal = reachable.stream().map(nodes::get)
            .anyMatch(node -> node.configuration().path("terminal").asBoolean(false));
        if (!reachedTerminal) {
            findings.add(new SimulationFinding("NO_FINALIZATION", "ERROR", null,
                "No terminal node is reachable"));
        }
        boolean valid = findings.stream().noneMatch(finding -> "ERROR".equals(finding.severity()));
        return new WorkflowSimulationResult(valid, List.copyOf(visited), List.copyOf(findings));
    }

    private void walk(WorkflowGraphNode entry, Map<UUID, WorkflowGraphNode> nodes,
                      Map<UUID, List<WorkflowGraphTransition>> outgoing,
                      WorkflowSimulationInput input, List<String> visited,
                      Set<UUID> reachable, List<SimulationFinding> findings) {
        ArrayDeque<WorkflowGraphNode> queue = new ArrayDeque<>();
        Map<UUID, Integer> visits = new HashMap<>();
        queue.add(entry);
        while (!queue.isEmpty()) {
            WorkflowGraphNode node = queue.removeFirst();
            int count = visits.merge(node.id(), 1, Integer::sum);
            if (count > input.maximumVisitsPerNode()) {
                findings.add(new SimulationFinding("POSSIBLE_CYCLE", "ERROR", node.code(),
                    "Node exceeded the configured simulation visit limit"));
                continue;
            }
            reachable.add(node.id());
            visited.add(node.code());
            List<WorkflowGraphTransition> selected = outgoing.getOrDefault(node.id(), List.of())
                .stream()
                .filter(transition -> conditions.evaluate(input.conditionContext(),
                    transition.condition()).matched())
                .sorted(java.util.Comparator.comparingInt(
                    WorkflowGraphTransition::priority).reversed())
                .toList();
            if (!node.configuration().path("terminal").asBoolean(false) && selected.isEmpty()) {
                findings.add(new SimulationFinding("CONDITION_DEAD_END", "ERROR", node.code(),
                    "No outgoing condition matched the supplied data"));
            }
            if (node.configuration().path("exclusive").asBoolean(false) && selected.size() > 1
                && selected.get(0).priority() == selected.get(1).priority()) {
                findings.add(new SimulationFinding("CONFLICTING_CONDITION", "ERROR", node.code(),
                    "Exclusive node has multiple matching transitions at equal priority"));
            }
            int limit = node.configuration().path("exclusive").asBoolean(false)
                ? Math.min(1, selected.size()) : selected.size();
            for (int index = 0; index < limit; index++) {
                WorkflowGraphNode target = nodes.get(selected.get(index).targetNodeId());
                if (target != null) {
                    queue.addLast(target);
                }
            }
        }
    }
}
