package org.agrfesta.sh.api

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.springframework.stereotype.Component

@AnalyzeClasses(packages = ["org.agrfesta.sh.api.persistence", "org.agrfesta.sh.api.cache"], importOptions = [DoNotIncludeTests::class])
class PersistenceArchitectureTest {

    companion object {

        private val outboundPortFromCore: DescribedPredicate<JavaClass> =
            DescribedPredicate.describe("an outbound port from core.application.ports.outbounds") { javaClass ->
                javaClass.packageName.startsWith("org.agrfesta.sh.api.core.application.ports.outbounds")
            }

        @JvmField
        @ArchTest
        val adaptersImplementOutboundPorts: ArchRule = classes()
            .that().resideInAnyPackage("..persistence..adapters..", "..cache..adapters..")
            .and().areMetaAnnotatedWith(Component::class.java)
            .should().implement(outboundPortFromCore)
            .because("Adapter classes must implement an outbound port interface from core; misplaced helpers belong elsewhere")
    }
}
