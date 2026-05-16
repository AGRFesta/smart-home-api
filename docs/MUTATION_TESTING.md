# Mutation Testing with PITest

Mutation testing verifies that tests can detect real logic defects, not just that code is executed.
PITest introduces small deliberate faults (mutants) into production code; a surviving mutant is a
gap in the test suite that line coverage alone cannot reveal.

## Covered modules

| Module        | Command                       | Mutation score | Test strength | Threshold |
|---------------|-------------------------------|---------------|--------------|-----------|
| `:core`       | `./gradlew :core:pitest`      | 70 %          | 76 %         | 70        |
| `:providers`  | `./gradlew :providers:pitest` | 43 %          | 60 %         | 43        |

Scores above are the baseline established on the initial run (2026-05-16, STRONGER mutator set).

## Excluded modules

| Module         | Reason                                                                                       |
|----------------|----------------------------------------------------------------------------------------------|
| `:persistence` | All tests are Testcontainers-based integration tests. Running PITest would spin up a Docker container per mutant — impractical. Correctness is validated by the integration suite instead. Test classes do not follow an `*IT` naming convention, so `excludedTestClasses` cannot be used to carve out a fast unit-test subset. |
| `:app`         | End-to-end integration tests only. No isolated unit tests to run mutation analysis against.  |

## Configuration

```kotlin
plugins {
    alias(libs.plugins.pitest)
}

pitest {
    pitestVersion       = "1.17.3"           // PIT engine (separate from the Gradle plugin)
    junit5PluginVersion = "1.2.1"            // required for JUnit 5 test discovery
    targetClasses       = listOf("org.agrfesta.sh.*")
    excludedClasses     = listOf("org.agrfesta.sh.*Test*", "org.agrfesta.sh.*Fixtures*")
    avoidCallsTo        = setOf("kotlin.jvm.internal")
    mutators            = listOf("STRONGER")
    outputFormats       = listOf("HTML", "XML")
    timestampedReports  = false
    threads             = 4
    useClasspathFile    = true   // avoids Windows long-path issues
    mutationThreshold   = <value>  // build fails if killed% drops below this
}
```

Reports are generated under `<module>/build/reports/pitest/index.html`.

## Kotlin limitations

PITest operates on JVM bytecode, not source code. Kotlin compiles correctly to bytecode, but the
compiler generates a significant amount of synthetic code that has no direct equivalent in the
source. Without a Kotlin-aware plugin, PITest mutates this generated code too, producing mutants
that survive not because of missing tests but because the code is inherently untestable:

| Generated construct | Example | Why mutants survive |
|---|---|---|
| Null-safety checks | `Intrinsics.checkNotNullParameter(...)` | Test data never passes `null` for non-nullable params |
| `data class` boilerplate | `copy$default` bit-masking for default params | Compiler-generated branching logic, not authored logic |
| Companion object accessors | `access$getX$cp()` synthetic methods | Not callable from tests |
| `when` on sealed classes | extra `else` branch added by the compiler | Statically unreachable |

The practical effect is that the mutation score is **slightly pessimistic**: survived mutants
include genuine test gaps *and* compiler artifacts. Killed mutants, on the other hand, are always
real — a test that kills a mutant is valid evidence of quality regardless of the plugin.

The `avoidCallsTo = setOf("kotlin.jvm.internal")` setting already mitigates the most common case
(null-safety checks). The remaining noise can be identified by inspecting the HTML report: compiler
artifacts are typically found in `copy$default`, synthetic accessor methods, or unreachable `else`
branches of `when` expressions.

The `com.groupcdg.pitest:pitest-kotlin-plugin` (now the commercial **Arcmutate** product) would
filter these automatically, but it requires a paid license and is not included. The Arcmutate
warning in the build output can be safely ignored.

## Interpreting surviving mutants

Not every surviving mutant indicates a missing test. Some are semantically equivalent mutations
(e.g. changing `>` to `>=` in a boundary that is never exercised in practice). Review the HTML
report before adding tests: chase gaps in logic, not 100% kill rate.

## Raising the threshold

After improving the test suite, re-run pitest, read the new score, and update `mutationThreshold`
in the relevant `build.gradle.kts`. Keep the threshold at or just below the actual score so any
future regression fails the build.
