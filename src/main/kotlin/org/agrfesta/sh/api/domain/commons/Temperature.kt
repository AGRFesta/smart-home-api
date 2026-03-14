package org.agrfesta.sh.api.domain.commons

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class Temperature private constructor(val value: BigDecimal) : Comparable<Temperature> {

    companion object {
        fun of(value: BigDecimal) = Temperature(
                value
                    .setScale(2, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
            )

        fun of(stringValue: String): Temperature = of(BigDecimal(stringValue))
    }

    override fun compareTo(other: Temperature): Int = this.value.compareTo(other.value)

    operator fun plus(other: Temperature): Temperature = of(this.value + other.value)

    operator fun minus(other: Temperature): Temperature = of(this.value - other.value)

    operator fun times(other: BigDecimal): Temperature = of(this.value * other)

    operator fun div(other: BigDecimal): Temperature = of(this.value / other)
}

fun Collection<Temperature>.average(): Temperature? {
    if (isEmpty()) return null
    val average = fold(BigDecimal.ZERO) { acc, num -> acc + num.value }
        .divide(BigDecimal(size), 2, RoundingMode.HALF_UP)
    return Temperature.of(average)
}
