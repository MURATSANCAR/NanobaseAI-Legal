package com.nanobase.specai;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:1/unavailable",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://127.0.0.1:1/test"
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

    @Test
    void migrationsBucketsQueuesAndRedisAreReady() throws Exception {
        List<String> tables = jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'",
            String.class);
        assertThat(tables).contains(
            "tender_project", "project_member", "document", "document_version",
            "external_document_mapping", "outbox_event", "audit_event", "processed_event");
        assertThat(minio.bucketExists(
            BucketExistsArgs.builder().bucket("specai-original").build())).isTrue();
        assertThat(rabbitAdmin.getQueueInfo("document-processing")).isNotNull();
        assertThat(rabbitAdmin.getQueueInfo("document-processing.dlq")).isNotNull();
        try (var connection = redis.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
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
