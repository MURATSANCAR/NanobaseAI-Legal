package com.nanobase.specai;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.nanobase.specai.tender.infrastructure.ProjectCodeGenerator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
    "specai.storage.create-buckets=true",
    "specai.bootstrap.enabled=true",
    "specai.bootstrap.tenant-id=11111111-1111-1111-1111-111111111111",
    "specai.bootstrap.tenant-name=Integration Test Tenant",
    "specai.document-intelligence.enabled=false",
    "specai.security.auth-mode=local",
    "specai.security.jwt.secret=integration-test-jwt-secret-32bytes!!",
    "specai.security.local.admin-password=integration-test-admin-password"
})
@Testcontainers(disabledWithoutDocker = true)
class PlatformInfrastructureIT {
    private static final String PASSWORD = "integration-test-password";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.5-alpine")
            .withDatabaseName("specai")
            .withUsername("specai")
            .withPassword(PASSWORD);

    @Container
    static final RabbitMQContainer RABBIT =
        new RabbitMQContainer("rabbitmq:4.1.0-management-alpine")
            .withUser("specai", PASSWORD)
            .withPermission("/", "specai", ".*", ".*", ".*");

    @Container
    static final MinIOContainer MINIO =
        new MinIOContainer("minio/minio:RELEASE.2025-04-22T22-12-26Z")
            .withUserName("specai")
            .withPassword(PASSWORD);

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:8.0.2-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", PASSWORD);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "specai");
        registry.add("spring.rabbitmq.password", () -> PASSWORD);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> PASSWORD);
        registry.add("specai.storage.endpoint", MINIO::getS3URL);
        registry.add("specai.storage.access-key", MINIO::getUserName);
        registry.add("specai.storage.secret-key", MINIO::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MinioClient minio;
    @Autowired RabbitAdmin rabbitAdmin;
    @Autowired RedisConnectionFactory redis;
    @Autowired ProjectCodeGenerator projectCodes;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void migrationsBucketsQueuesAndRedisAreReady() throws Exception {
        List<String> tables = jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'",
            String.class);
        assertThat(tables).contains(
            "tender_project", "project_member", "document", "document_version",
            "external_document_mapping", "outbox_event", "audit_event", "processed_message",
            "document_processing_job", "processing_event", "document_page", "clause",
            "document_table", "parser_warning", "ontology", "ontology_version",
            "ontology_concept", "terminology_catalog", "terminology_entry",
            "policy_definition", "policy_version", "analysis_profile",
            "requirement_extraction_job", "requirement", "requirement_revision",
            "expert_feedback", "evaluation_dataset", "evaluation_case",
            "terminology_snapshot", "knowledge_entity", "entity_attribute",
            "knowledge_relation", "capability", "evidence_fragment", "evidence_claim",
            "evidence_validity_assessment", "knowledge_snapshot",
            "compliance_evaluation", "compliance_evidence_link",
            "risk_taxonomy", "risk_taxonomy_version", "risk_analysis_profile",
            "risk_analysis_job", "risk_record", "risk_source", "risk_factor",
            "risk_revision", "ambiguity_finding", "conflict_record",
            "requirement_dependency", "document_change_set", "document_change_item",
            "impact_analysis_job", "impact_analysis_result",
            "analysis_staleness_record", "risk_propagation_candidate",
            "mitigation_catalog", "clarification_strategy",
            "workflow_definition", "workflow_version", "workflow_node",
            "workflow_transition", "workflow_instance", "workflow_token",
            "workflow_execution", "workflow_transition_log", "workflow_simulation_run",
            "task_record", "task_dependency", "task_comment", "task_attachment",
            "assignment_policy", "business_role", "approval_request",
            "approval_step", "approval_decision", "sla_policy", "task_sla_record",
            "business_calendar", "notification_rule", "notification_delivery",
            "clarification_request", "clarification_revision", "clarification_answer",
            "report_definition", "report_definition_version",
            "report_section_definition", "report_data_snapshot",
            "report_generation_job", "report_artifact", "decision_support_case",
            "executive_decision", "project_finalization_record",
            "dashboard_definition", "dashboard_widget",
            "feature_definition", "feature_assignment", "quota_definition",
            "quota_assignment", "rate_limit_policy", "backpressure_policy",
            "file_security_assessment", "prompt_security_assessment",
            "quality_gate_definition", "quality_gate_version",
            "evaluation_result_item", "shadow_execution", "canary_assignment",
            "recovery_policy", "retention_policy", "data_classification_policy",
            "go_live_checklist_item");
        assertThat(minio.bucketExists(
            BucketExistsArgs.builder().bucket("specai-original").build())).isTrue();
        assertThat(rabbitAdmin.getQueueInfo("document-processing.request")).isNotNull();
        assertThat(rabbitAdmin.getQueueInfo("document-processing.result")).isNotNull();
        assertThat(rabbitAdmin.getQueueInfo("document-processing.dlq")).isNotNull();
        assertThat(rabbitAdmin.getQueueInfo("requirement-extraction.request")).isNotNull();
        assertThat(rabbitAdmin.getQueueInfo("risk-analysis.request")).isNotNull();
        try (var connection = redis.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }

    @Test
    void ontologyAndTerminologyAreTenantIsolatedWhileGlobalBaselineIsVisible() {
        UUID organizationA = UUID.randomUUID();
        UUID organizationB = UUID.randomUUID();
        UUID ontologyId = UUID.randomUUID();
        UUID catalogId = UUID.randomUUID();
        jdbc.update("insert into organization (id, name) values (?, ?)",
            organizationA, "Analysis Tenant A");
        jdbc.update("insert into organization (id, name) values (?, ?)",
            organizationB, "Analysis Tenant B");
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(ignored -> {
            jdbc.queryForObject(
                "select set_config('app.current_organization_id', ?, true)",
                String.class, organizationA.toString());
            jdbc.update("""
                insert into ontology (
                    id, organization_id, code, name, scope, status, created_at, updated_at
                ) values (?, ?, 'TENANT_ONLY', 'Tenant only', 'ORGANIZATION',
                          'ACTIVE', now(), now())
                """, ontologyId, organizationA);
            jdbc.update("""
                insert into terminology_catalog (
                    id, organization_id, name, scope, status, created_at, updated_at
                ) values (?, ?, 'Tenant terminology', 'ORGANIZATION',
                          'ACTIVE', now(), now())
                """, catalogId, organizationA);
        });
        Map<String, Integer> counts = transactions.execute(ignored -> {
            jdbc.queryForObject(
                "select set_config('app.current_organization_id', ?, true)",
                String.class, organizationB.toString());
            return Map.of(
                "tenantOntology", jdbc.queryForObject(
                    "select count(*) from ontology where id = ?",
                    Integer.class, ontologyId),
                "tenantTerminology", jdbc.queryForObject(
                    "select count(*) from terminology_catalog where id = ?",
                    Integer.class, catalogId),
                "globalOntology", jdbc.queryForObject(
                    "select count(*) from ontology where code = 'BASE_REQUIREMENT'",
                    Integer.class));
        });
        assertThat(counts).containsEntry("tenantOntology", 0)
            .containsEntry("tenantTerminology", 0)
            .containsEntry("globalOntology", 1);
    }

    @Test
    void rlsBlocksCrossTenantSqlAccess() {
        UUID organizationA = UUID.randomUUID();
        UUID organizationB = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        jdbc.update("insert into organization (id, name) values (?, ?)",
            organizationA, "Tenant A");
        jdbc.update("insert into organization (id, name) values (?, ?)",
            organizationB, "Tenant B");
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(ignored -> {
            jdbc.queryForObject(
                "select set_config('app.current_organization_id', ?, true)",
                String.class, organizationA.toString());
            jdbc.update("""
                insert into tender_project (
                    id, organization_id, project_code, name, institution_name,
                    priority, status, created_by, owner_user_id,
                    created_at, updated_at, version
                ) values (?, ?, ?, ?, ?, 'NORMAL', 'DRAFT', 'owner', 'owner',
                          now(), now(), 0)
                """, projectId, organizationA, "RLS-" + UUID.randomUUID(),
                "RLS project", "Institution");
        });
        Integer visible = transactions.execute(ignored -> {
            jdbc.queryForObject(
                "select set_config('app.current_organization_id', ?, true)",
                String.class, organizationB.toString());
            return jdbc.queryForObject(
                "select count(*) from tender_project where id = ?",
                Integer.class, projectId);
        });
        assertThat(visible).isZero();
    }

    @Test
    void productionAssignmentsAreTenantIsolatedAndAuditChainVerifies() {
        UUID organizationA = UUID.randomUUID();
        UUID organizationB = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        jdbc.update("insert into organization (id, name) values (?, ?)",
            organizationA, "Production Tenant A");
        jdbc.update("insert into organization (id, name) values (?, ?)",
            organizationB, "Production Tenant B");
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(ignored -> {
            jdbc.queryForObject("select set_config('app.current_organization_id', ?, true)",
                String.class, organizationA.toString());
            jdbc.update("""
                insert into tender_project (
                    id, organization_id, project_code, name, institution_name,
                    priority, status, created_by, owner_user_id,
                    created_at, updated_at, version
                ) values (?, ?, ?, 'Production controls', 'Synthetic Institution',
                          'NORMAL', 'DRAFT', 'owner', 'owner', now(), now(), 0)
                """, projectId, organizationA, "PROD-" + UUID.randomUUID());
            jdbc.update("""
                insert into feature_assignment (
                    id, organization_id, project_id, feature_definition_id, enabled,
                    configuration_json, created_at, updated_at
                ) values (?, ?, ?, '81000000-0000-0000-0000-000000000005',
                          true, '{}'::jsonb, now(), now())
                """, UUID.randomUUID(), organizationA, projectId);
            jdbc.update("""
                insert into audit_event (
                    id, organization_id, user_id, event_type, entity_type, entity_id,
                    created_at, after_json, correlation_id
                ) values (?, ?, 'integration', 'CONTROL_CREATED', 'Project', ?,
                          now(), '{}'::jsonb, ?)
                """, UUID.randomUUID(), organizationA, projectId, UUID.randomUUID());
        });
        Integer foreignVisible = transactions.execute(ignored -> {
            jdbc.queryForObject("select set_config('app.current_organization_id', ?, true)",
                String.class, organizationB.toString());
            return jdbc.queryForObject(
                "select count(*) from feature_assignment where project_id = ?",
                Integer.class, projectId);
        });
        Boolean chainValid = transactions.execute(ignored -> {
            jdbc.queryForObject("select set_config('app.current_organization_id', ?, true)",
                String.class, organizationA.toString());
            return jdbc.queryForObject(
                "select valid from verify_audit_chain(?)",
                Boolean.class, organizationA);
        });
        assertThat(foreignVisible).isZero();
        assertThat(chainValid).isTrue();
    }

    @Test
    void skipLockedPreventsTwoPublishersFromClaimingSameOutboxRow() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        jdbc.update("insert into organization (id, name) values (?, ?)",
            organizationId, "Outbox Tenant");
        jdbc.update("""
            insert into outbox_event (
                id, event_id, aggregate_type, aggregate_id, event_type, event_version,
                routing_key, payload_json, organization_id, correlation_id, status,
                retry_count, next_attempt_at, created_at, updated_at, version
            ) values (?, ?, 'Document', ?, 'Requested', 1, ?, '{}'::jsonb, ?, ?,
                      'PENDING', 0, now(), now(), now(), 0)
            """, eventId, eventId, UUID.randomUUID(),
            "document.processing.requested.v1", organizationId, UUID.randomUUID());
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        java.util.concurrent.CountDownLatch firstLocked =
            new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch secondRead =
            new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> transactions.execute(ignored -> {
                List<UUID> claimed = jdbc.queryForList("""
                    select id from outbox_event
                    where status = 'PENDING' and next_attempt_at <= now()
                    order by created_at for update skip locked limit 1
                    """, UUID.class);
                firstLocked.countDown();
                try {
                    secondRead.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return claimed;
            }));
            var second = executor.submit(() -> {
                firstLocked.await();
                List<UUID> claimed = transactions.execute(ignored -> jdbc.queryForList("""
                    select id from outbox_event
                    where status = 'PENDING' and next_attempt_at <= now()
                    order by created_at for update skip locked limit 1
                    """, UUID.class));
                secondRead.countDown();
                return claimed;
            });
            assertThat(first.get()).containsExactly(eventId);
            assertThat(second.get()).isEmpty();
        }
    }

    @Test
    void projectCodesRemainUniqueUnderConcurrency() throws Exception {
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 50)
                .mapToObj(ignored -> (java.util.concurrent.Callable<String>) projectCodes::next)
                .toList();
            var codes = executor.invokeAll(tasks).stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();
            assertThat(codes).doesNotHaveDuplicates()
                .allMatch(code -> code.matches("TND-\\d{4}-\\d{6,}"));
        }
    }
}
