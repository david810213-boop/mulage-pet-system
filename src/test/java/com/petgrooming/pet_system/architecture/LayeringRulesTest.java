package com.petgrooming.pet_system.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 分層規則：以測試把關，避免新程式碼破壞分層。
 *
 * CONTROLLERS_STILL_USING_REPOSITORIES 是重構計畫 2-4 項的「現況白名單」：
 * 重構時每移走一支 controller 的 Repository 依賴，就從這份清單刪掉它；
 * 清單清空即代表該項完成。新增的 controller 不得加入此清單。
 */
class LayeringRulesTest {

    private static final String BASE = "com.petgrooming.pet_system";

    private static final Set<String> CONTROLLERS_STILL_USING_REPOSITORIES = Set.of(
            "AdminMemberController",
            "CatBreedCoatMappingMvcController",
            "CatRewashMvcController",
            "CustomerAnalysisMvcController",
            "DashboardMvcController",
            "MobileDailyController",
            "MobileMemberController",
            "MobileScheduleController",
            "MobileWalkInController",
            "PaymentMvcController",
            "PetEditMvcController",
            "WalletMvcController");

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
                .and(notInLegacyAllowList())
                .should().dependOnClassesThat().resideInAPackage(BASE + ".repository..")
                .because("Controller 應透過 Service 存取資料（重構計畫 2-4）")
                .check(classes);
    }

    private static DescribedPredicate<JavaClass> notInLegacyAllowList() {
        return DescribedPredicate.describe("are not in the legacy allow-list",
                javaClass -> !CONTROLLERS_STILL_USING_REPOSITORIES.contains(javaClass.getSimpleName()));
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
