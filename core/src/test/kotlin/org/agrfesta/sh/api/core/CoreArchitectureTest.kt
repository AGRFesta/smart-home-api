package org.agrfesta.sh.api.core

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

@Suppress("UtilityClassWithPublicConstructor")
@AnalyzeClasses(
    packages = ["org.agrfesta.sh.api.core.domain", "org.agrfesta.sh.api.core.application.usecases"],
    importOptions = [DoNotIncludeTests::class]
)
class CoreArchitectureTest {

    companion object {

        private val springClassesExceptService: DescribedPredicate<JavaClass> =
            DescribedPredicate.describe("Spring classes other than @Service") { javaClass ->
                javaClass.packageName.startsWith("org.springframework") &&
                    javaClass.name != "org.springframework.stereotype.Service"
            }

        @JvmField
        @ArchTest
        val domainIsSpringFree: ArchRule = noClasses()
            .that().resideInAPackage("..core.domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .because("The domain layer must have zero knowledge of Spring")

        @JvmField
        @ArchTest
        val usecasesOnlyUseServiceAnnotation: ArchRule = noClasses()
            .that().resideInAPackage("..core.application.usecases..")
            .should().dependOnClassesThat(springClassesExceptService)
            .because(
                "Only @Service is permitted in core use case classes; " +
                    "use UnitOfWork for transactions, never @Transactional"
            )
    }
}
