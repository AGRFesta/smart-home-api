package org.agrfesta.sh.api.domain.commons

import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.math.BigDecimal.ZERO

@JvmInline
value class Percentage(val value: BigDecimal) {
    init {
        require(value >= ZERO && value <= ONE) { "Percentage must be between 0 and 1, is $value." }
    }

    fun asText(): String = value.toString()

    override fun toString(): String {
        var hundreds = value.movePointRight(2)
        if (hundreds.scale() > 0) hundreds = hundreds.stripTrailingZeros()
        return "$hundreds%"
    }

    companion object {
        fun of(percentage: String) = Percentage(BigDecimal(percentage))
        fun ofHundreds(value: BigDecimal) = Percentage(value.movePointLeft(2).stripTrailingZeros())
        fun ofHundreds(value: Int) = ofHundreds(BigDecimal(value))
        fun ofHundreds(value: String) = ofHundreds(BigDecimal(value))
    }
}
