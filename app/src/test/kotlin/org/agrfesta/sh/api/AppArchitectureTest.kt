package org.agrfesta.sh.api

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

@Suppress("UtilityClassWithPublicConstructor")
@AnalyzeClasses(packages = ["org.agrfesta.sh.api.controllers"], importOptions = [DoNotIncludeTests::class])
class AppArchitectureTest {

    companion object {

        @JvmField
        @ArchTest
        val controllersDoNotDependOnInfrastructure: ArchRule = noClasses()
            .that().resideInAPackage("..controllers..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..persistence..",
                "..providers..",
                "..cache.."
            )
            .because("Controllers must depend only on inbound port interfaces, never on infrastructure adapters")
    }
}
