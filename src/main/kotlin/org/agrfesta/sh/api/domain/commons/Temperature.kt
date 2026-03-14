package org.agrfesta.sh.api.domain.commons

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class Temperature private constructor(val value: BigDecimal) : Comparable<Temperature> {

    companion object {
        fun of(value: BigDecimal): Temperature {
            val formatted = value
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
            return Temperature(formatted)
        }

        fun of(stringValue: String): Temperature {
            return of(BigDecimal(stringValue))
        }
    }

    override fun compareTo(other: Temperature): Int = this.value.compareTo(other.value)

    operator fun plus(other: Temperature): Temperature = Temperature(this.value + other.value)

    operator fun minus(other: Temperature): Temperature = Temperature(this.value - other.value)

    fun times(other: BigDecimal): Temperature = Temperature(this.value * other)

    fun div(other: BigDecimal): Temperature = Temperature(this.value / other)
}

fun Collection<Temperature>.average(): Temperature? {
    if (isEmpty()) return null
    val average = fold(BigDecimal.ZERO) { acc, num -> acc + num.value }
        .divide(BigDecimal(size), 2, RoundingMode.HALF_UP)
    return Temperature.of(average)
}
