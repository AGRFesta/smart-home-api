package org.agrfesta.test.property

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigDecimal
import io.kotest.property.arbitrary.map
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.commons.Temperature
import java.math.BigDecimal
import java.math.RoundingMode

private val ZERO: BigDecimal = BigDecimal.ZERO
private val ONE: BigDecimal = BigDecimal.ONE

/**
 * Working precision of percentages in the domain: matches `AVERAGE_SCALE` used by
 * `Collection<Percentage>.average()` and the `aRandomPercentage(scale = 10)` object mother. Generating
 * values at this scale keeps them consistent with the precision domain operations actually preserve, so
 * rounding-sensitive invariants (e.g. average within [min, max]) hold exactly rather than being broken
 * by sub-ULP rounding artifacts.
 */
private const val PERCENTAGE_SCALE = 10

/**
 * Domain generators used by property-based tests. They feed the value-under-test into the existing
 * object mothers (which already accept values as parameters); the mothers are left untouched.
 * Unlike the `Random`-based mothers, these `Arb`s support shrinking to a minimal counterexample.
 */

fun Arb.Companion.percentage(): Arb<Percentage> =
    Arb.bigDecimal(ZERO, ONE).map { Percentage(it.setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP)) }

fun Arb.Companion.temperature(
    min: BigDecimal = BigDecimal("-100"),
    max: BigDecimal = BigDecimal("100")
): Arb<Temperature> =
    Arb.bigDecimal(min, max).map { Temperature.of(it) }
