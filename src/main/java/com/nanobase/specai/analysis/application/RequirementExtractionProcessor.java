package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.analysis.application.AnalysisModels.ClauseAnalysisContext;
import com.nanobase.specai.analysis.application.AnalysisModels.ClauseSignalResult;
import com.nanobase.specai.analysis.application.AnalysisModels.ConfidenceContext;
import com.nanobase.specai.analysis.application.AnalysisModels.ConfidenceResult;
import com.nanobase.specai.analysis.application.AnalysisModels.ConceptMatch;
import com.nanobase.specai.analysis.application.AnalysisModels.DuplicateCandidate;
import com.nanobase.specai.analysis.application.AnalysisModels.DuplicateResult;
import com.nanobase.specai.analysis.application.AnalysisModels.ExtractionRequest;
import com.nanobase.specai.analysis.application.AnalysisModels.ExtractionResponse;
import com.nanobase.specai.analysis.application.AnalysisModels.ExtractionStrategy;
import com.nanobase.specai.analysis.application.AnalysisModels.GroundingInput;
import com.nanobase.specai.analysis.application.AnalysisModels.GroundingResult;
import com.nanobase.specai.analysis.application.AnalysisModels.ModelRoutingContext;
import com.nanobase.specai.analysis.application.AnalysisModels.ModelRoutingResult;
import com.nanobase.specai.analysis.application.AnalysisModels.PolicyDocument;
import com.nanobase.specai.analysis.application.AnalysisModels.PromptMaterial;
import com.nanobase.specai.analysis.application.AnalysisModels.TerminologyMatch;
import com.nanobase.specai.analysis.application.AnalysisModels.UnitMatch;
import com.nanobase.specai.analysis.domain.AnalysisProfile;
import com.nanobase.specai.analysis.domain.AnalysisProfileRepository;
import com.nanobase.specai.analysis.domain.Requirement;
import com.nanobase.specai.analysis.domain.EmptyExtractionOutcome;
import com.nanobase.specai.analysis.domain.RequirementExtractionJob;
import com.nanobase.specai.analysis.domain.RequirementExtractionJobRepository;
import com.nanobase.specai.analysis.domain.RequirementRepository;
import com.nanobase.specai.analysis.domain.RequirementRevision;
import com.nanobase.specai.analysis.domain.RequirementRevisionRepository;
import com.nanobase.specai.analysis.infrastructure.AnalysisPersistenceStore;
import com.nanobase.specai.analysis.integration.AnalysisEvents.AnalysisProgress;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.document.domain.Clause;
import com.nanobase.specai.document.domain.ClauseRepository;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import com.nanobase.specai.operations.application.FeatureFlagService;
import com.nanobase.specai.operations.application.TenderIntelligenceFlags;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequirementExtractionProcessor {
    private final TenantDatabaseContext tenantDatabase;
    private final RequirementExtractionJobRepository jobs;
    private final AnalysisProfileRepository profiles;
    private final ClauseRepository clauses;
    private final ClauseSignalFeatureExtractor featureExtractor;
    private final ClauseSignalEvaluator signalEvaluator;
    private final ClauseContextBuilder contextBuilder;
    private final ExtractionStrategyResolver strategyResolver;
    private final ModelRoutingEngine modelRouter;
    private final GroundingValidator groundingValidator;
    private final ConfidencePolicyEngine confidenceEngine;
    private final DynamicOutputSchemaValidator schemaValidator;
    private final DuplicateDetectionEngine duplicateDetection;
    private final AnalysisCatalogPort catalog;
    private final AiGateway aiGateway;
    private final PromptSecurityService promptSecurity;
    private final RequirementRepository requirements;
    private final RequirementRevisionRepository revisions;
    private final AnalysisPersistenceStore store;
    private final OutboxService outbox;
    private final ObjectMapper mapper;
    private final FeatureFlagService featureFlags;
    private final RequirementClassificationValidator classificationValidator;
    private final RequirementConditionExtractor conditionExtractor;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock = Clock.systemUTC();

    public RequirementExtractionProcessor(
        TenantDatabaseContext tenantDatabase,
        RequirementExtractionJobRepository jobs,
        AnalysisProfileRepository profiles,
        ClauseRepository clauses,
        ClauseSignalFeatureExtractor featureExtractor,
        ClauseSignalEvaluator signalEvaluator,
        ClauseContextBuilder contextBuilder,
        ExtractionStrategyResolver strategyResolver,
        ModelRoutingEngine modelRouter,
        GroundingValidator groundingValidator,
        ConfidencePolicyEngine confidenceEngine,
        DynamicOutputSchemaValidator schemaValidator,
        DuplicateDetectionEngine duplicateDetection,
        AnalysisCatalogPort catalog,
        AiGateway aiGateway,
        PromptSecurityService promptSecurity,
        RequirementRepository requirements,
        RequirementRevisionRepository revisions,
        AnalysisPersistenceStore store,
        OutboxService outbox,
        ObjectMapper mapper,
        FeatureFlagService featureFlags,
        RequirementClassificationValidator classificationValidator,
        RequirementConditionExtractor conditionExtractor,
        AuditService audit,
        JdbcTemplate jdbc) {
        this.tenantDatabase = tenantDatabase;
        this.jobs = jobs;
        this.profiles = profiles;
        this.clauses = clauses;
        this.featureExtractor = featureExtractor;
        this.signalEvaluator = signalEvaluator;
        this.contextBuilder = contextBuilder;
        this.strategyResolver = strategyResolver;
        this.modelRouter = modelRouter;
        this.groundingValidator = groundingValidator;
        this.confidenceEngine = confidenceEngine;
        this.schemaValidator = schemaValidator;
        this.duplicateDetection = duplicateDetection;
        this.catalog = catalog;
        this.aiGateway = aiGateway;
        this.promptSecurity = promptSecurity;
        this.requirements = requirements;
        this.revisions = revisions;
        this.store = store;
        this.outbox = outbox;
        this.mapper = mapper;
        this.featureFlags = featureFlags;
        this.classificationValidator = classificationValidator;
        this.conditionExtractor = conditionExtractor;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional
    public void process(UUID organizationId, UUID jobId) {
        tenantDatabase.apply(organizationId);
        RequirementExtractionJob job = jobs.findForUpdate(jobId, organizationId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Requirement extraction job not found: " + jobId));
        if (!"QUEUED".equals(job.status())) {
            return;
        }
        AnalysisProfile profile = profiles.findByIdAndOrganizationId(
            job.analysisProfileId(), organizationId)
            .orElseThrow(() -> new IllegalStateException("Analysis profile snapshot is missing"));
        List<Clause> sourceClauses =
            clauses.findAllByDocumentVersionIdAndOrganizationIdOrderBySortOrder(
                job.documentVersionId(), organizationId);
        Instant now = clock.instant();
        job.start(sourceClauses.size(), now);
        store.event(organizationId, job.id(), "STARTED", 0,
            "Requirement extraction started",
            Map.of("totalClauseCount", sourceClauses.size()), now);
        outbox.publish(organizationId, "RequirementExtraction", job.id(),
            "RequirementExtractionStarted", RabbitConfiguration.REQUIREMENT_STARTED,
            progressPayload(job), job.correlationId());

        int processed = 0;
        int extracted = 0;
        int reviews = 0;
        int classificationFailures = 0;
        int suspiciousEmpty = 0;
        int timeoutEmpty = 0;
        int schemaFailures = 0;
        String dominantEmptyOutcome = null;
        // Prefer sentence windows; early-stop after consecutive model timeouts.
        int consecutiveTimeouts = 0;
        try {
            for (Clause clause : sourceClauses) {
                ClauseSignalResult signal = signalEvaluator.evaluate(
                    featureExtractor.extract(clause, profile));
                if ("EXTRACT".equals(signal.recommendedAction())) {
                    try {
                        ClauseOutcome outcome = extractClause(job, profile, clause, signal);
                        extracted += outcome.extracted();
                        reviews += outcome.reviews();
                        classificationFailures += outcome.classificationFailures();
                        consecutiveTimeouts = 0;
                        if (outcome.emptyOutcomeCode() != null) {
                            dominantEmptyOutcome = outcome.emptyOutcomeCode();
                            if (EmptyExtractionOutcome.SUSPICIOUS_EMPTY
                                .equals(outcome.emptyOutcomeCode())) {
                                suspiciousEmpty++;
                                reviews++;
                                store.event(organizationId, job.id(), "SUSPICIOUS_EMPTY",
                                    percent(processed, sourceClauses.size()),
                                    "High-signal clause produced empty extraction",
                                    Map.of("clauseId", clause.id(),
                                        "signalScore", signal.signalScore(),
                                        "emptyOutcome", outcome.emptyOutcomeCode()),
                                    clock.instant());
                            } else if (EmptyExtractionOutcome.TIMEOUT_EMPTY
                                .equals(outcome.emptyOutcomeCode())) {
                                timeoutEmpty++;
                                consecutiveTimeouts++;
                            } else if (EmptyExtractionOutcome.SCHEMA_FAILURE
                                .equals(outcome.emptyOutcomeCode())) {
                                schemaFailures++;
                            }
                        }
                    } catch (RuntimeException clauseFailure) {
                        reviews++;
                        String failureMessage = String.valueOf(clauseFailure.getMessage())
                            .toLowerCase(java.util.Locale.ROOT);
                        boolean timedOut = failureMessage.contains("timeout")
                            || failureMessage.contains("timed out")
                            || failureMessage.contains("deadline");
                        if (timedOut) {
                            timeoutEmpty++;
                            consecutiveTimeouts++;
                            dominantEmptyOutcome = EmptyExtractionOutcome.TIMEOUT_EMPTY;
                        } else {
                            consecutiveTimeouts = 0;
                            dominantEmptyOutcome = EmptyExtractionOutcome.MODEL_FAILURE;
                        }
                        store.event(organizationId, job.id(), "CLAUSE_REVIEW_REQUIRED",
                            percent(processed, sourceClauses.size()),
                            "Clause extraction requires manual review",
                            Map.of("clauseId", clause.id(),
                                "errorCode", safeCode(clauseFailure),
                                "errorMessage", truncateMessage(clauseFailure),
                                "emptyOutcome", timedOut
                                    ? EmptyExtractionOutcome.TIMEOUT_EMPTY
                                    : EmptyExtractionOutcome.MODEL_FAILURE),
                            clock.instant());
                    }
                } else if ("MANUAL_REVIEW".equals(signal.recommendedAction())) {
                    reviews++;
                    consecutiveTimeouts = 0;
                    store.event(organizationId, job.id(), "CLAUSE_REVIEW_REQUIRED",
                        percent(processed, sourceClauses.size()),
                        "Clause signal policy requested manual review",
                        Map.of("clauseId", clause.id(), "signalScore", signal.signalScore()),
                        clock.instant());
                }
                processed++;
                job.progress(processed, extracted, reviews, clock.instant());
                store.event(organizationId, job.id(), "PROGRESS",
                    percent(processed, sourceClauses.size()),
                    "Requirement extraction progress", progressPayload(job), clock.instant());
                outbox.publish(organizationId, "RequirementExtraction", job.id(),
                    "RequirementExtractionProgress", RabbitConfiguration.REQUIREMENT_PROGRESS,
                    progressPayload(job), job.correlationId());
                if (extracted == 0 && consecutiveTimeouts >= 3) {
                    store.event(organizationId, job.id(), "TIMEOUT_ABORT",
                        percent(processed, sourceClauses.size()),
                        "Aborting after consecutive model timeouts",
                        Map.of("consecutiveTimeouts", consecutiveTimeouts),
                        clock.instant());
                    break;
                }
            }
            if (extracted == 0 && (suspiciousEmpty > 0 || timeoutEmpty > 0)) {
                store.event(organizationId, job.id(), "SUSPICIOUS_EMPTY_JOB", 100,
                    "Job completed with empty extractions after high-signal or timeout outcomes",
                    Map.of(
                        "suspiciousEmpty", suspiciousEmpty,
                        "timeoutEmpty", timeoutEmpty,
                        "schemaFailures", schemaFailures,
                        "emptyOutcome", dominantEmptyOutcome == null
                            ? EmptyExtractionOutcome.SUSPICIOUS_EMPTY
                            : dominantEmptyOutcome),
                    clock.instant());
            }
            jdbc.update("""
                update requirement_extraction_job
                set empty_outcome_code = ?, suspicious_empty_count = ?,
                    timeout_empty_count = ?, schema_failure_count = ?
                where id = ? and organization_id = ?
                """, dominantEmptyOutcome, suspiciousEmpty, timeoutEmpty, schemaFailures,
                job.id(), organizationId);
            if (extracted == 0 && timeoutEmpty > 0) {
                job.fail(clock.instant());
                store.event(organizationId, job.id(), "FAILED", 100,
                    "Requirement extraction failed due to model timeouts",
                    Map.of("timeoutEmpty", timeoutEmpty,
                        "emptyOutcome", EmptyExtractionOutcome.TIMEOUT_EMPTY),
                    clock.instant());
                outbox.publish(organizationId, "RequirementExtraction", job.id(),
                    "RequirementExtractionFailed", RabbitConfiguration.REQUIREMENT_FAILED,
                    Map.of("jobId", job.id(), "errorCode", "TIMEOUT_EMPTY",
                        "timeoutEmpty", timeoutEmpty),
                    job.correlationId());
                return;
            }
            if (classificationFailures > 0 || suspiciousEmpty > 0 || timeoutEmpty > 0) {
                job.partiallyComplete(clock.instant());
                store.event(organizationId, job.id(), "PARTIALLY_COMPLETED", 100,
                    "Requirement extraction completed with review-required outcomes",
                    Map.of("classificationFailures", classificationFailures,
                        "suspiciousEmpty", suspiciousEmpty,
                        "timeoutEmpty", timeoutEmpty), clock.instant());
            } else {
                job.complete(clock.instant());
                store.event(organizationId, job.id(), "COMPLETED", 100,
                    "Requirement extraction completed", progressPayload(job), clock.instant());
            }
            outbox.publish(organizationId, "RequirementExtraction", job.id(),
                "RequirementExtractionCompleted", RabbitConfiguration.REQUIREMENT_COMPLETED,
                progressPayload(job), job.correlationId());
            if (reviews > 0) {
                outbox.publish(organizationId, "RequirementExtraction", job.id(),
                    "RequirementReviewRequired", RabbitConfiguration.REQUIREMENT_REVIEW,
                    progressPayload(job), job.correlationId());
            }
        } catch (RuntimeException failure) {
            job.fail(clock.instant());
            store.event(organizationId, job.id(), "FAILED",
                percent(processed, sourceClauses.size()),
                "Requirement extraction failed",
                Map.of("errorCode", safeCode(failure)), clock.instant());
            outbox.publish(organizationId, "RequirementExtraction", job.id(),
                "RequirementExtractionFailed", RabbitConfiguration.REQUIREMENT_FAILED,
                Map.of("jobId", job.id(), "errorCode", safeCode(failure)),
                job.correlationId());
        }
    }

    private ClauseOutcome extractClause(RequirementExtractionJob job, AnalysisProfile profile,
                                        Clause clause, ClauseSignalResult signal) {
        List<String> chunks = ClauseChunker.chunk(
            clause.rawText(), ClauseChunker.DEFAULT_MAX_CHARS, ClauseChunker.DEFAULT_OVERLAP_CHARS);
        if (chunks.isEmpty()) {
            return new ClauseOutcome(0, 0, 0, EmptyExtractionOutcome.LOW_SIGNAL_EMPTY);
        }
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            jdbc.update("""
                insert into clause_chunk (
                    id, organization_id, clause_id, chunk_index, text_content,
                    token_count, context_header_json, source_block_ids_json, created_at
                ) values (?, ?, ?, ?, ?, ?, '{}', '[]', now())
                """, UUID.randomUUID(), job.organizationId(), clause.id(), i,
                chunkText, chunkText.length() / 4);
        }
        int extracted = 0;
        int reviews = 0;
        int classificationFailures = 0;
        boolean schemaFailed = false;
        boolean timedOut = false;
        boolean modelFailed = false;
        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
            String chunkText = chunks.get(chunkIndex);
            try {
                ClauseOutcome chunkOutcome = extractClauseChunk(
                    job, profile, clause, signal, chunkText, chunkIndex, chunks.size());
                extracted += chunkOutcome.extracted();
                reviews += chunkOutcome.reviews();
                classificationFailures += chunkOutcome.classificationFailures();
                if (EmptyExtractionOutcome.SCHEMA_FAILURE.equals(chunkOutcome.emptyOutcomeCode())) {
                    schemaFailed = true;
                }
            } catch (RuntimeException failure) {
                String message = String.valueOf(failure.getMessage()).toLowerCase(Locale.ROOT);
                if (message.contains("timeout")) {
                    timedOut = true;
                } else {
                    modelFailed = true;
                }
                throw failure;
            }
            // Stop early once we have grounded requirements from a chunk.
            if (extracted > 0) {
                break;
            }
        }
        String empty = EmptyExtractionOutcome.classify(
            signal.signalScore(), timedOut, schemaFailed, modelFailed, extracted);
        return new ClauseOutcome(extracted, reviews, classificationFailures, empty);
    }

    private ClauseOutcome extractClauseChunk(
        RequirementExtractionJob job, AnalysisProfile profile, Clause clause,
        ClauseSignalResult signal, String chunkText, int chunkIndex, int chunkCount) {
        ClauseAnalysisContext context = contextBuilder.build(clause, profile);
        ExtractionStrategy strategy = strategyResolver.resolve(context, profile);
        PolicyDocument routingPolicy = catalog.policy(profile.organizationId(),
            profile.modelRoutingPolicyId());
        Map<String, Double> routingSignals = new HashMap<>();
        routingSignals.put("clauseSignal", signal.signalScore());
        routingSignals.put("chunkIndex", (double) chunkIndex);
        routingSignals.put("chunkCount", (double) chunkCount);
        routingSignals.put("chunkChars", (double) chunkText.length());
        context.features().forEach((key, value) -> {
            if (value instanceof Number number) {
                routingSignals.put(key, number.doubleValue());
            } else if (value instanceof Boolean bool) {
                routingSignals.put(key, bool ? 1d : 0d);
            }
        });
        ModelRoutingResult routing = modelRouter.route(profile.organizationId(),
            new ModelRoutingContext(Map.copyOf(routingSignals),
                Map.of("requiredProfile", strategy.modelProfile())), routingPolicy);
        UUID routingId = store.routingDecision(profile.organizationId(), job.id(), clause.id(),
            routing, routingPolicy.id(), routingSignals, clock.instant());
        PromptMaterial prompt = catalog.prompt(profile.organizationId(),
            strategy.promptPackageVersionId());
        JsonNode schema = catalog.outputSchema(profile.organizationId(),
            profile.outputSchemaVersionId());
        List<String> assembledPrompt = new ArrayList<>(assemblePrompt(profile, clause, prompt));
        assembledPrompt.add("Trusted clause chunk " + (chunkIndex + 1) + "/" + chunkCount
            + " (untrusted document content follows between delimiters):\n"
            + "<<<DOCUMENT_CHUNK>>>\n" + chunkText + "\n<<<END_DOCUMENT_CHUNK>>>");
        PromptSecurityService.Assessment promptAssessment = promptSecurity.assess(
            profile.organizationId(), job.documentVersionId(), clause.id(), profile.id(),
            job.correlationId(), Map.of(
                "contentClassification", "UNTRUSTED_DOCUMENT",
                "clauseText", chunkText,
                "selectedContext", context.selectedContext()));
        if ("PENDING".equals(promptAssessment.reviewStatus())) {
            store.event(profile.organizationId(), job.id(), "PROMPT_SECURITY_REVIEW_REQUIRED", 0,
                "Instruction-like document content requires human review",
                Map.of("clauseId", clause.id(), "signalScore", promptAssessment.signalScore(),
                    "signals", promptAssessment.signals()), clock.instant());
        }
        Instant modelStarted = clock.instant();
        ExtractionResponse response = aiGateway.extract(new ExtractionRequest(
            job.id(), profile.organizationId(), clause.id(), AiGateway.LOGICAL_MODEL,
            routing.profileCode(), List.copyOf(assembledPrompt), schema, context,
            strategy.maximumOutputTokens(), job.correlationId()));
        List<String> schemaErrors = schemaValidator.validate(schema, response.output());
        store.modelRun(profile.organizationId(), job.id(), clause, routingId,
            profile.promptPackageVersionId(), profile.outputSchemaVersionId(), response,
            schemaErrors.isEmpty(), schemaErrors.isEmpty() ? null : "SCHEMA_VALIDATION_FAILED",
            modelStarted);
        if (!schemaErrors.isEmpty()) {
            store.event(profile.organizationId(), job.id(), "SCHEMA_REJECTED", 0,
                "Model output rejected by active output schema",
                Map.of("clauseId", clause.id(), "errors", schemaErrors,
                    "chunkIndex", chunkIndex), clock.instant());
            return new ClauseOutcome(0, 1, 0, EmptyExtractionOutcome.SCHEMA_FAILURE);
        }

        int extracted = 0;
        int reviews = 0;
        int classificationFailures = 0;
        for (JsonNode candidate : response.output().path("requirements")) {
            PersistOutcome outcome = persistCandidate(job, profile, clause, signal, context,
                strategy, routing, response, schema, candidate);
            extracted += outcome.persisted() ? 1 : 0;
            reviews += outcome.requiresReview() ? 1 : 0;
            classificationFailures += outcome.classificationFailed() ? 1 : 0;
        }
        return new ClauseOutcome(extracted, reviews, classificationFailures, null);
    }

    private PersistOutcome persistCandidate(
        RequirementExtractionJob job, AnalysisProfile profile, Clause clause,
        ClauseSignalResult signal, ClauseAnalysisContext context, ExtractionStrategy strategy,
        ModelRoutingResult routing, ExtractionResponse response, JsonNode schema,
        JsonNode candidate) {
        List<String> fragments = new ArrayList<>();
        candidate.path("sourceFragments").forEach(item -> fragments.add(item.asText()));
        PolicyDocument extractionPolicy = catalog.policy(profile.organizationId(),
            profile.policyVersionId());
        List<String> excludedPaths = new ArrayList<>();
        extractionPolicy.configuration().path("grounding").path("excludedPaths")
            .forEach(item -> excludedPaths.add(item.asText()));
        GroundingResult grounding = groundingValidator.validate(new GroundingInput(
            clause.rawText(), fragments, candidate,
            Map.of("excludedPaths", excludedPaths,
                "pageStart", clause.pageStart(), "pageEnd", clause.pageEnd())));
        if ("UNGROUNDED".equals(grounding.status())) {
            store.event(profile.organizationId(), job.id(), "UNGROUNDED_REJECTED", 0,
                "Ungrounded requirement candidate rejected",
                Map.of("clauseId", clause.id(),
                    "unsupportedFields", grounding.unsupportedFields()), clock.instant());
            return new PersistOutcome(false, true, false);
        }

        String conceptCode = text(candidate, "primaryConceptCode");
        Optional<ConceptMatch> concept = conceptCode == null ? Optional.empty()
            : catalog.concept(profile.organizationId(), profile.ontologyVersionId(),
                conceptCode);
        String modalityCode = text(candidate, "modalityConceptCode");
        Optional<ConceptMatch> modality = modalityCode == null ? Optional.empty()
            : catalog.concept(profile.organizationId(), profile.ontologyVersionId(),
                modalityCode);
        UnitResolution units = resolveUnits(candidate, schema, profile);
        Map<String, Double> confidenceFactors = new HashMap<>();
        confidenceFactors.put("groundingCoverage", grounding.coverage());
        confidenceFactors.put("schemaValidation", 1d);
        confidenceFactors.put("clauseSignal", signal.signalScore());
        double ocrQuality = profileOcrQuality(profile);
        confidenceFactors.put("parserQuality", ocrQuality);
        confidenceFactors.put("ocrQuality", ocrQuality);
        if (conceptCode != null) {
            confidenceFactors.put("ontologyMatch", concept.isPresent() ? 1d : 0d);
        }
        if (units.configuredCount() > 0) {
            confidenceFactors.put("unitValidation",
                (double) units.resolved().size() / units.configuredCount());
        }
        PolicyDocument confidencePolicy = catalog.policy(profile.organizationId(),
            profile.confidencePolicyId());
        ConfidenceResult confidence = confidenceEngine.calculate(
            new ConfidenceContext(Map.copyOf(confidenceFactors)), confidencePolicy);
        DuplicateResult duplicate = duplicateResult(job, clause, candidate, concept, extractionPolicy);
        boolean requiresReview = confidence.requiresReview()
            || !"GROUNDED".equals(grounding.status())
            || !units.unresolvedPaths().isEmpty()
            || !"UNIQUE".equals(duplicate.status());
        String requirementCode = requiredText(candidate, "requirementCode");
        if (requirements.existsByExtractionJobIdAndSourceClauseIdAndRequirementCode(
            job.id(), clause.id(), requirementCode)) {
            return new PersistOutcome(false, true, false);
        }

        String requirementText = requiredText(candidate, "requirementText");
        ObjectNode explanation = mapper.createObjectNode();
        explanation.set("analysisProfile", read(profile.snapshotJson()));
        explanation.set("clauseSignals", mapper.valueToTree(signal));
        explanation.set("selectedContext", mapper.valueToTree(context.selectedContext()));
        explanation.put("extractionStrategy", strategy.code());
        explanation.put("modelProfile", routing.profileCode());
        explanation.set("modelRoutingReasons", mapper.valueToTree(routing.reasonCodes()));
        explanation.set("grounding", mapper.valueToTree(grounding));
        explanation.set("unitResolution", mapper.valueToTree(units));
        explanation.set("duplicateAssessment", mapper.valueToTree(duplicate));
        explanation.set("confidence", mapper.valueToTree(confidence));

        Instant now = clock.instant();
        Requirement requirement = new Requirement(UUID.randomUUID(), profile.organizationId(),
            job.projectId(), job.documentId(), job.documentVersionId(), job.id(), clause.id(),
            requirementCode, text(candidate, "title"), requirementText,
            normalize(requirementText), concept.map(ConceptMatch::id).orElse(null),
            modalityCode, modality.map(ConceptMatch::id).orElse(null),
            text(candidate, "testabilityStatus"), text(candidate, "conditionText"),
            text(candidate, "subjectText"), text(candidate, "actionText"),
            text(candidate, "objectText"), json(candidate.path("attributes")),
            strategy.code(), requiresReview ? "PENDING_REVIEW" : "READY_FOR_REVIEW",
            grounding.status(), grounding.coverage(), profile, response.modelRunId(),
            confidence.score(), json(explanation), now);
        requirements.saveAndFlush(requirement);

        boolean classificationFailed = false;
        if (featureFlags.enabled(profile.organizationId(), job.projectId(),
            TenderIntelligenceFlags.REQUIREMENT_CLASSIFICATION)) {
            try {
                var proposal = RequirementClassificationValidator.ClassificationProposal
                    .fromCandidate(candidate);
                var classified = classificationValidator.validate(proposal,
                    profile.organizationId(), job.projectId());
                if (classified.status()
                    == RequirementClassificationValidator.Status.FAILED) {
                    classificationFailed = true;
                }
                classificationValidator.apply(requirement, classified, now);
                if (classified.evaluationMethod()
                    == com.nanobase.specai.analysis.domain.EvaluationMethod.NUMERIC_THRESHOLD
                    || classified.evaluationMethod()
                    == com.nanobase.specai.analysis.domain.EvaluationMethod.MULTI_CONDITION
                    || classified.evaluationMethod()
                    == com.nanobase.specai.analysis.domain.EvaluationMethod.CERTIFICATE_VALIDITY
                    || classified.evaluationMethod()
                    == com.nanobase.specai.analysis.domain.EvaluationMethod.PERSONNEL_COUNT
                    || classified.evaluationMethod()
                    == com.nanobase.specai.analysis.domain.EvaluationMethod.EXPERIENCE_DURATION
                    || classified.evaluationMethod()
                    == com.nanobase.specai.analysis.domain.EvaluationMethod.DATE_VALIDITY) {
                    var conditions = conditionExtractor.extractAndPersist(
                        profile.organizationId(), requirement.id(), requirementText, candidate);
                    if (!conditions.succeeded()
                        && classified.evaluationMethod()
                        == com.nanobase.specai.analysis.domain.EvaluationMethod.NUMERIC_THRESHOLD) {
                        requirement.classify(classified.requirementType(),
                            classified.obligationLevel(), classified.lifecycleStage(),
                            classified.criticality(),
                            com.nanobase.specai.analysis.domain.EvaluationMethod.MANUAL_REVIEW,
                            classified.consequenceType(), classified.remediability(),
                            classified.responsibleDepartment(), classified.deadlineType(),
                            classified.explicitDeadline(), classified.relativeDeadlineDays(),
                            classified.scoringWeight(), classified.minimumScore(),
                            classified.closedWorldRequired(), classified.requiresClarification(),
                            now);
                        requirement.classificationStatus(
                            RequirementClassificationValidator.Status.REVIEW_REQUIRED.name());
                    } else if (conditions.succeeded()) {
                        audit.recordSystem(profile.organizationId(), AiGateway.LOGICAL_MODEL,
                            "REQUIREMENT_CONDITIONS_EXTRACTED", "Requirement", requirement.id(),
                            null, Map.of("conditionCount", conditions.conditions().size(),
                                "projectId", job.projectId()));
                    }
                }
                requirements.saveAndFlush(requirement);
                audit.recordSystem(profile.organizationId(), AiGateway.LOGICAL_MODEL,
                    "REQUIREMENT_CLASSIFIED", "Requirement", requirement.id(), null,
                    Map.of("classificationStatus", requirement.classificationStatus(),
                        "obligationLevel", requirement.obligationLevel().name(),
                        "projectId", job.projectId()));
                if (classified.status()
                    == RequirementClassificationValidator.Status.REVIEW_REQUIRED) {
                    requiresReview = true;
                }
            } catch (RuntimeException classificationError) {
                classificationFailed = true;
                var failed = classificationValidator.failed();
                classificationValidator.apply(requirement, failed, now);
                requirements.saveAndFlush(requirement);
                store.event(profile.organizationId(), job.id(), "CLASSIFICATION_FAILED", 0,
                    "Requirement classification failed; defaults applied",
                    Map.of("requirementId", requirement.id(),
                        "errorCode", safeCode(classificationError)), now);
            }
        }

        store.sourceFragments(requirement, clause, fragments, grounding.evidence(), now);
        revisions.save(new RequirementRevision(UUID.randomUUID(), profile.organizationId(),
            requirement.id(), 1, snapshot(requirement), "AI_EXTRACTION",
            response.modelRunId(), AiGateway.LOGICAL_MODEL, now));
        return new PersistOutcome(true, requiresReview, classificationFailed);
    }

    private DuplicateResult duplicateResult(
        RequirementExtractionJob job, Clause clause, JsonNode candidate,
        Optional<ConceptMatch> concept, PolicyDocument extractionPolicy) {
        DuplicateCandidate current = new DuplicateCandidate(null,
            normalize(requiredText(candidate, "requirementText")),
            concept.map(ConceptMatch::id).orElse(null), candidate.path("attributes"),
            clause.id(), job.documentVersionId());
        DuplicateResult strongest = new DuplicateResult("UNIQUE", 0, List.of());
        for (Requirement existing : requirements.findAllByExtractionJobIdAndOrganizationId(
            job.id(), job.organizationId())) {
            DuplicateCandidate previous = new DuplicateCandidate(existing.id(),
                existing.normalizedRequirementText(), existing.primaryConceptId(),
                read(existing.attributesJson()), existing.sourceClauseId(),
                existing.documentVersionId());
            DuplicateResult compared = duplicateDetection.compare(
                current, previous, extractionPolicy);
            if (compared.score() > strongest.score()) {
                strongest = compared;
            }
        }
        return strongest;
    }

    private List<String> assemblePrompt(AnalysisProfile profile, Clause clause,
                                        PromptMaterial prompt) {
        PolicyConfiguration configuration = new PolicyConfiguration(
            catalog.policy(profile.organizationId(), profile.policyVersionId()).configuration());
        int conceptLimit = configuration.requiredInteger(
            "promptContext.ontologyConceptLimit");
        double minimum = configuration.requiredNumber(
            "promptContext.minimumConceptRelevance");
        List<Map<String, Object>> concepts = catalog.ontologyConcepts(
            profile.organizationId(), profile.ontologyVersionId()).stream()
            .map(concept -> Map.entry(concept,
                lexicalRelevance(clause.normalizedText(),
                    String.valueOf(concept.get("conceptCode")) + " "
                        + String.valueOf(concept.get("name")) + " "
                        + String.valueOf(concept.get("description")))))
            .filter(item -> item.getValue() >= minimum)
            .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
            .limit(conceptLimit)
            .map(Map.Entry::getKey)
            .toList();
        List<TerminologyMatch> terminology = featureExtractor.matches(clause, profile);
        List<String> components = new ArrayList<>(prompt.components());
        components.add("Trusted analysis profile metadata:\n" + profile.snapshotJson());
        components.add("Relevant approved terminology:\n" + json(terminology));
        components.add("Relevant active ontology concepts:\n" + json(concepts));
        return List.copyOf(components);
    }

    private UnitResolution resolveUnits(JsonNode candidate, JsonNode schema,
                                        AnalysisProfile profile) {
        List<String> paths = new ArrayList<>();
        schema.path("x-grounding").path("unitAliasPaths")
            .forEach(item -> paths.add(item.asText()));
        List<Map<String, Object>> resolved = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        String language = read(profile.documentContextJson()).path("language").asText("und");
        for (String path : paths) {
            JsonNode aliasNode = at(candidate, path);
            if (!aliasNode.isTextual() || aliasNode.textValue().isBlank()) {
                unresolved.add(path);
                continue;
            }
            Optional<UnitMatch> unit = catalog.unitByAlias(
                profile.organizationId(), language, normalize(aliasNode.textValue()));
            if (unit.isEmpty()) {
                unresolved.add(path);
                continue;
            }
            UnitMatch match = unit.get();
            Map<String, Object> item = new HashMap<>();
            item.put("path", path);
            item.put("alias", aliasNode.textValue());
            item.put("unitId", match.id());
            item.put("canonicalCode", match.canonicalCode());
            if (match.symbol() != null) {
                item.put("symbol", match.symbol());
            }
            if (match.dimension() != null) {
                item.put("dimension", match.dimension());
            }
            resolved.add(Map.copyOf(item));
        }
        return new UnitResolution(paths.size(), List.copyOf(resolved),
            List.copyOf(unresolved));
    }

    private JsonNode at(JsonNode root, String dottedPath) {
        JsonNode current = root;
        for (String component : dottedPath.split("\\.")) {
            current = current.path(component);
        }
        return current;
    }

    private double lexicalRelevance(String left, String right) {
        java.util.Set<String> leftTokens = tokens(left);
        java.util.Set<String> rightTokens = tokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0;
        }
        java.util.Set<String> intersection = new java.util.HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        java.util.Set<String> union = new java.util.HashSet<>(leftTokens);
        union.addAll(rightTokens);
        return (double) intersection.size() / union.size();
    }

    private java.util.Set<String> tokens(String value) {
        java.util.Set<String> tokens = new java.util.HashSet<>();
        if (value != null) {
            for (String token : value.toLowerCase(Locale.ROOT)
                .split("[^\\p{L}\\p{N}]+")) {
                if (!token.isBlank()) {
                    tokens.add(token);
                }
            }
        }
        return tokens;
    }

    private Map<String, Object> progressPayload(RequirementExtractionJob job) {
        return Map.of("jobId", job.id(), "status", job.status(),
            "progress", percent(job.processedClauseCount(), job.totalClauseCount()),
            "processedClauses", job.processedClauseCount(),
            "extractedRequirements", job.extractedRequirementCount(),
            "manualReviews", job.manualReviewCount());
    }

    private double profileOcrQuality(AnalysisProfile profile) {
        return read(profile.documentContextJson()).path("ocrQuality").asDouble(0);
    }

    private String snapshot(Requirement requirement) {
        return json(Map.ofEntries(
            Map.entry("id", requirement.id()),
            Map.entry("requirementCode", requirement.requirementCode()),
            Map.entry("requirementText", requirement.requirementText()),
            Map.entry("attributes", read(requirement.attributesJson())),
            Map.entry("reviewStatus", requirement.reviewStatus()),
            Map.entry("groundingStatus", requirement.groundingStatus()),
            Map.entry("combinedConfidence", requirement.combinedConfidence())
        ));
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalArgumentException("Model output field is blank: " + field);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.textValue().isBlank()
            ? value.textValue() : null;
    }

    private JsonNode read(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored analysis JSON is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Analysis result cannot be serialized", exception);
        }
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private int percent(int processed, int total) {
        return total == 0 ? 100 : Math.min(100, (int) Math.floor(100d * processed / total));
    }

    private String safeCode(Throwable failure) {
        String value = failure.getClass().getSimpleName()
            .replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        return value.substring(0, Math.min(160, value.length()));
    }

    private String truncateMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? failure.toString() : root.getMessage();
        return message.substring(0, Math.min(500, message.length()));
    }

    private record ClauseOutcome(int extracted, int reviews, int classificationFailures,
                                 String emptyOutcomeCode) {
    }

    private record PersistOutcome(boolean persisted, boolean requiresReview,
                                  boolean classificationFailed) {
    }

    private record UnitResolution(int configuredCount,
                                  List<Map<String, Object>> resolved,
                                  List<String> unresolvedPaths) {
    }
}
