package org.agrfesta.sh.api.core

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.CompositeArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

/**
 * Freezes the dependency matrix between `core/domain` packages — the subdomain boundaries
 * identified in `docs/SUBDOMAINS.md` and documented in `docs/ARCHITECTURE.md`
 * ("Domain Dependency Matrix").
 *
 * | package         | may depend on (within `core/domain`)   |
 * |-----------------|-----------------------------------------|
 * | `commons`       | nothing — shared kernel                  |
 * | `devices`       | `commons`                                |
 * | `areas`         | `commons`                                |
 * | `alerts`        | `commons`, `devices`                     |
 * | `heating`       | `commons`, `areas`, `devices`, `failures`|
 * | `notifications` | `commons`, `alerts`                      |
 * | `failures`      | any — cross-cutting failure catalogue    |
 *
 * [matrix] is the single source of truth: every subdomain package is a row, rules and the
 * package coverage check are derived from it, so a new `core/domain` package cannot appear
 * without a decided row. `UNRESTRICTED` rows (`failures`) generate no outgoing rule; cycles
 * through them are still caught by [domainPackagesAreFreeOfCycles].
 */
@Suppress("UtilityClassWithPublicConstructor")
@AnalyzeClasses(
    packages = ["org.agrfesta.sh.api.core"],
    importOptions = [DoNotIncludeTests::class]
)
class DomainDependencyMatrixTest {

    companion object {

        private val UNRESTRICTED: Set<String>? = null

        private data class Row(val pkg: String, val allowed: Set<String>?, val because: String)

        private val matrix = listOf(
            Row(
                pkg = "commons",
                allowed = emptySet(),
                because = "commons is the shared kernel: every subdomain may use it, " +
                    "it must know none of them"
            ),
            Row(
                pkg = "devices",
                allowed = setOf("commons"),
                because = "the device inventory is an upstream subdomain: heating and alerts " +
                    "observe devices, never the reverse (the devices ↔ alerts cycle was " +
                    "removed by #240)"
            ),
            Row(
                pkg = "areas",
                allowed = setOf("commons"),
                because = "areas are pure spatial grouping: device assignment is coordinated " +
                    "by the application layer, so no direct domain dependency on devices " +
                    "is sanctioned"
            ),
            Row(
                pkg = "alerts",
                allowed = setOf("commons", "devices"),
                because = "alerts observe device state (e.g. battery); how heating, areas or " +
                    "notifications react to an alert is not their concern"
            ),
            Row(
                pkg = "heating",
                allowed = setOf("commons", "areas", "devices", "failures"),
                because = "heating is the core subdomain orchestrating areas and devices; it " +
                    "reports actuator problems through the cross-cutting failure catalogue"
            ),
            Row(
                pkg = "notifications",
                allowed = setOf("commons", "alerts"),
                because = "notifications deliver alert transitions; delivery must stay " +
                    "ignorant of the subdomains that raise the alerts"
            ),
            Row(
                pkg = "failures",
                allowed = UNRESTRICTED,
                because = "failures is the cross-cutting catalogue: payloads may carry types " +
                    "from any subdomain; cycles through it are caught by the no-cycles rule"
            )
        )

        private val domainPackages: Set<String> = matrix.map(Row::pkg).let { pkgs ->
            val duplicates = pkgs.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            require(duplicates.isEmpty()) {
                "duplicate matrix row(s) for $duplicates — edit the existing row instead of adding a second one"
            }
            pkgs.toSet()
        }

        private fun domainPackage(name: String) = "..core.domain.$name.."

        private fun Row.toRule(): ArchRule? {
            val allowed = allowed ?: return null
            val forbidden = (domainPackages - pkg - allowed).sorted()
            require(forbidden.isNotEmpty()) {
                "row '$pkg' forbids nothing — model it as UNRESTRICTED instead"
            }
            return noClasses()
                .that().resideInAPackage(domainPackage(pkg))
                .should().dependOnClassesThat()
                .resideInAnyPackage(*forbidden.map(::domainPackage).toTypedArray())
                .because(because)
        }

        @JvmField
        @ArchTest
        val domainDependencyMatrixIsRespected: ArchRule = matrix
            .mapNotNull { it.toRule() }
            .let { rules ->
                rules.drop(1).fold(CompositeArchRule.of(rules.first())) { acc, rule -> acc.and(rule) }
            }
            .`as`("core/domain packages respect the subdomain dependency matrix")

        @JvmField
        @ArchTest
        val everyDomainClassBelongsToAKnownSubdomain: ArchRule = classes()
            .that().resideInAPackage("..core.domain..")
            .should().resideInAnyPackage(*domainPackages.map(::domainPackage).toTypedArray())
            .because(
                "every core/domain package is a subdomain boundary: a new package must be " +
                    "added to the dependency matrix (this test) before it can be used"
            )

        @JvmField
        @ArchTest
        val domainPackagesAreFreeOfCycles = slices()
            .matching("..core.domain.(*)..")
            .should().beFreeOfCycles()
            .`as`("domain packages should be free of cycles")
            .because(
                "cyclic dependencies between domain packages blur the subdomain boundaries " +
                    "(e.g. the devices ↔ alerts cycle removed by #240)"
            )
    }
}
