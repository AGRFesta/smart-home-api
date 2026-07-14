package org.agrfesta.sh.api.core

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

@Suppress("UtilityClassWithPublicConstructor")
@AnalyzeClasses(
    packages = ["org.agrfesta.sh.api.core"],
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

        @JvmField
        @ArchTest
        val domainIsLoggingFree: ArchRule = noClasses()
            .that().resideInAPackage("..core.domain..")
            .should().dependOnClassesThat().resideInAPackage("org.slf4j..")
            .because("Logging belongs to the application/adapters, not to the domain")

        @JvmField
        @ArchTest
        val domainDoesNotDependOnApplication: ArchRule = noClasses()
            .that().resideInAPackage("..core.domain..")
            .should().dependOnClassesThat().resideInAPackage("..core.application..")
            .because(
                "The domain layer must be completely independent of the application layer (ports and use cases)"
            )

        @JvmField
        @ArchTest
        val noDtosOrViewsInDomain: ArchRule = noClasses()
            .that().resideInAPackage("..core.domain..")
            .should().haveSimpleNameEndingWith("Dto")
            .orShould().haveSimpleNameEndingWith("View")
            .because(
                "DTOs and read-models are application concerns; " +
                    "they belong in core.application.readmodels, not in the domain"
            )

        private val notAcceptReadModelParameters: ArchCondition<JavaMethod> =
            object : ArchCondition<JavaMethod>(
                "not accept parameters involving core.application.readmodels types"
            ) {
                override fun check(method: JavaMethod, events: ConditionEvents) {
                    val readModels = method.parameterTypes
                        .flatMap { it.allInvolvedRawTypes }
                        .filter { it.packageName.contains("core.application.readmodels") }
                    if (readModels.isNotEmpty()) {
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                "${method.fullName} accepts read-model(s) " +
                                    readModels.joinToString { it.simpleName }
                            )
                        )
                    }
                }
            }

        @JvmField
        @ArchTest
        val readModelsAreNeverInputToOutboundPorts: ArchRule = methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..core.application.ports.outbounds..")
            .should(notAcceptReadModelParameters)
            .because(
                "read-models are query result shapes; " +
                    "outbound port inputs must be domain types, value objects or primitives"
            )
    }
}
