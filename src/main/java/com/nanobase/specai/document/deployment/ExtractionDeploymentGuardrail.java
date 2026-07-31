package com.nanobase.specai.document.deployment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Extraction deployment guardrail: document + compliance workers must fit the pool.
 * Does not alter compliance fencing; only validates configuration at startup when enabled.
 */
@Component
public class ExtractionDeploymentGuardrail implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ExtractionDeploymentGuardrail.class);

    private final boolean enabled;
    private final boolean enforce;
    private final int databasePoolSize;
    private final int documentExtractionWorkerConcurrency;
    private final int complianceWorkerConcurrency;
    private final int operationalHeadroom;

    public ExtractionDeploymentGuardrail(
        @Value("${specai.extraction.deployment.guardrails-enabled:false}") boolean enabled,
        @Value("${specai.extraction.deployment.enforce:false}") boolean enforce,
        @Value("${DATABASE_POOL_SIZE:${spring.datasource.hikari.maximum-pool-size:20}}")
        int databasePoolSize,
        @Value("${specai.extraction.worker-concurrency:1}") int documentExtractionWorkerConcurrency,
        @Value("${COMPLIANCE_WORKER_CONCURRENCY:1}") int complianceWorkerConcurrency,
        @Value("${COMPLIANCE_DB_OPERATIONAL_HEADROOM:2}") int operationalHeadroom
    ) {
        this.enabled = enabled;
        this.enforce = enforce;
        this.databasePoolSize = databasePoolSize;
        this.documentExtractionWorkerConcurrency = documentExtractionWorkerConcurrency;
        this.complianceWorkerConcurrency = complianceWorkerConcurrency;
        this.operationalHeadroom = operationalHeadroom;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        int required = documentExtractionWorkerConcurrency
            + complianceWorkerConcurrency
            + operationalHeadroom;
        boolean ok = databasePoolSize >= required;
        log.info(
            "event=EXTRACTION_DEPLOYMENT_GUARDRAIL ok={} databasePoolSize={} "
                + "documentExtractionWorkerConcurrency={} complianceWorkerConcurrency={} "
                + "operationalHeadroom={} required={}",
            ok, databasePoolSize, documentExtractionWorkerConcurrency,
            complianceWorkerConcurrency, operationalHeadroom, required);
        if (!ok && enforce) {
            throw new IllegalStateException(
                "Extraction deployment guardrail failed: databasePoolSize="
                    + databasePoolSize + " < required=" + required);
        }
    }
}
