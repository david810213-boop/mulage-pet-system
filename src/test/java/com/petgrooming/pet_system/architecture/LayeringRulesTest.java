package com.petgrooming.pet_system.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 分層規則：以測試把關，避免新程式碼破壞分層。
 */
class LayeringRulesTest {

    private static final String BASE = "com.petgrooming.pet_system";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void controllersDoNotAccessRepositoriesDirectly() {
        noClasses().that().resideInAPackage(BASE + ".controller..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".repository..")
                .because("Controller 應透過 Service 存取資料")
                .check(classes);
    }

    @Test
    void servicesDoNotDependOnControllers() {
        noClasses().that().resideInAPackage(BASE + ".service..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".controller..")
                .check(classes);
    }

    @Test
    void repositoriesDoNotDependOnServicesOrControllers() {
        noClasses().that().resideInAPackage(BASE + ".repository..")
                .should().dependOnClassesThat().resideInAnyPackage(BASE + ".service..", BASE + ".controller..")
                .check(classes);
    }

    @Test
    void entitiesDoNotDependOnUpperLayers() {
        noClasses().that().resideInAPackage(BASE + ".model..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".dto..", BASE + ".service..", BASE + ".controller..")
                .check(classes);
    }
}
