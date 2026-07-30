package com.nanobase.specai.compliance.application;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ComplianceTransactionBoundaryArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.nanobase.specai.compliance");

    @Test
    void processMustNotBeTransactional() throws Exception {
        Method process = ComplianceAnalysisProcessor.class.getMethod(
            "process", java.util.UUID.class, java.util.UUID.class, java.util.UUID.class);
        assertFalse(process.isAnnotationPresent(Transactional.class),
            "ComplianceAnalysisProcessor.process must not be @Transactional");
        assertFalse(ComplianceAnalysisProcessor.class.isAnnotationPresent(Transactional.class),
            "ComplianceAnalysisProcessor class must not be @Transactional");
    }

    @Test
    void processMethodMustNotCarryTransactionalAnnotationArchUnit() {
        methods().that().areDeclaredIn(ComplianceAnalysisProcessor.class)
            .and().haveName("process")
            .should().notBeAnnotatedWith(Transactional.class)
            .check(classes);
    }

    @Test
    void transactionServiceMustNotDependOnModelClient() {
        noClasses().that().haveSimpleName("ComplianceJobTransactionService")
            .should().dependOnClassesThat().haveSimpleNameContaining("ComplianceAiGateway")
            .orShould().dependOnClassesThat().haveSimpleNameContaining("HttpComplianceAi")
            .orShould().dependOnClassesThat().haveSimpleNameContaining("SemanticRouter")
            .orShould().dependOnClassesThat().haveSimpleNameContaining("ProfileSlotManager")
            .check(classes);
    }
}
