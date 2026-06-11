package org.agrfesta.test.annotations

import org.junit.jupiter.api.Tag

/**
 * Marks a property-based test (class or method). Bundles the JUnit 5 `@Tag("pbt")` so that PBT can be
 * filtered as a group — in particular they are excluded from pitest mutation analysis via
 * `excludedGroups = listOf("pbt")` (see issue.md). Property-based tests complement, and do not replace,
 * the example/table-based tests that remain the mutation oracle.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("pbt")
annotation class PropertyBasedTest
