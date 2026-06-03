package org.agrfesta.sh.api.providers

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Service

@AnalyzeClasses(packages = ["org.agrfesta.sh.api.providers"], importOptions = [DoNotIncludeTests::class])
class ProvidersArchitectureTest {

    companion object {

        @JvmField
        @ArchTest
        val providerServicesMustBeConditional: ArchRule = classes()
            .that().resideInAPackage("..providers..")
            .and().areAnnotatedWith(Service::class.java)
            .should().beMetaAnnotatedWith(Conditional::class.java)
            .because("Every provider @Service must be conditionally loaded to allow optional provider isolation")
    }
}
