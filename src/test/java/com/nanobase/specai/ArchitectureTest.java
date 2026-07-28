package com.nanobase.specai;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.nanobase.specai");

    @Test
    void domainDoesNotDependOnInfrastructureOrProviderAdapters() {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..infrastructure..", "..integration..", "..api..")
            .check(classes);
    }

    @Test
    void controllersCannotAccessRepositoriesDirectly() {
        noClasses().that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAssignableTo(Repository.class)
            .check(classes);
    }

    @Test
    void providerAdaptersStayOutsideDomainAndApi() {
        noClasses().that().resideInAnyPackage("..domain..", "..api..")
            .should().dependOnClassesThat().haveSimpleNameContaining("OpenContracts")
            .orShould().dependOnClassesThat().haveSimpleNameContaining("Docling")
            .check(classes);
    }

    @Test
    void apiMethodsDoNotReturnJpaEntities() {
        ArchRule rule = methods().that().areDeclaredInClassesThat()
            .areAnnotatedWith(RestController.class)
            .should().haveRawReturnType(
                com.tngtech.archunit.base.DescribedPredicate.describe(
                    "not be a JPA entity",
                    type -> !type.isAnnotatedWith(jakarta.persistence.Entity.class)));
        rule.check(classes);
    }

    @Test
    void documentRepositoriesRemainInterfaces() {
        classes().that().resideInAPackage("..document.domain")
            .and().haveSimpleNameEndingWith("Repository")
            .should().beInterfaces()
            .check(classes);
    }

    @Test
    void analysisDomainHasNoFixedTaxonomyOrRuntimeDependency() {
        noClasses().that().resideInAPackage("..analysis.domain..")
            .should().dependOnClassesThat().haveSimpleNameContaining("Taxonomy")
            .orShould().dependOnClassesThat().haveSimpleNameContaining("Ollama")
            .orShould().dependOnClassesThat().haveSimpleNameContaining("Llama")
            .orShould().dependOnClassesThat().haveSimpleNameContaining("Qwen")
            .check(classes);
    }

    @Test
    void modelRoutingIsNotImplementedInControllers() {
        noClasses().that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAssignableTo(
                com.nanobase.specai.analysis.application.ModelRoutingEngine.class)
            .check(classes);
    }

    @Test
    void analysisPoliciesAndCatalogsAreAccessedThroughPorts() {
        classes().that().haveSimpleName("AnalysisCatalogPort")
            .or().haveSimpleName("ClauseSignalEvaluator")
            .or().haveSimpleName("ClauseContextBuilder")
            .or().haveSimpleName("ExtractionStrategyResolver")
            .or().haveSimpleName("ConfidencePolicyEngine")
            .or().haveSimpleName("ModelRoutingEngine")
            .should().beInterfaces()
            .check(classes);
    }
}
