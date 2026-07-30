package com.nanobase.specai.compliance.application;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class CompliancePrepareExecutePersistArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.nanobase.specai.compliance");

    @Test
    void modelExecutionMustUsePropagationNever() throws Exception {
        Method execute = ComplianceTaskModelExecutionService.class
            .getMethod("execute", PreparedComplianceTask.class);
        Transactional transactional = execute.getAnnotation(Transactional.class);
        assertTrue(transactional != null);
        assertTrue(transactional.propagation() == Propagation.NEVER);
    }

    @Test
    void executionServiceMustNotDependOnJdbcOrEntityManager() {
        noClasses().that().haveSimpleName("ComplianceTaskModelExecutionService")
            .should().dependOnClassesThat().haveSimpleName("JdbcTemplate")
            .orShould().dependOnClassesThat().haveSimpleNameContaining("EntityManager")
            .orShould().dependOnClassesThat().haveSimpleNameContaining("JpaRepository")
            .check(classes);
    }

    @Test
    void preparedTaskMustNotReferenceJpaEntity() {
        noClasses().that().haveSimpleName("PreparedComplianceTask")
            .should().dependOnClassesThat()
            .areAnnotatedWith(jakarta.persistence.Entity.class)
            .check(classes);
    }

    @Test
    void processMustNotBeTransactional() throws Exception {
        Method process = ComplianceAnalysisProcessor.class.getMethod(
            "process", java.util.UUID.class, java.util.UUID.class, java.util.UUID.class);
        assertFalse(process.isAnnotationPresent(Transactional.class));
    }

    @Test
    void preparationAndPersistenceAreRequiresNew() throws Exception {
        Method prepare = null;
        for (Method method : ComplianceTaskPreparationService.class.getMethods()) {
            if ("prepare".equals(method.getName())) {
                prepare = method;
                break;
            }
        }
        assertTrue(prepare != null);
        assertTrue(prepare.getAnnotation(Transactional.class).propagation()
            == Propagation.REQUIRES_NEW);
        Method persist = ComplianceTaskPersistenceService.class.getMethod(
            "persist", PreparedComplianceTask.class, ComplianceExecutionResult.class,
            java.util.UUID.class);
        assertTrue(persist.getAnnotation(Transactional.class).propagation()
            == Propagation.REQUIRES_NEW);
    }
}
