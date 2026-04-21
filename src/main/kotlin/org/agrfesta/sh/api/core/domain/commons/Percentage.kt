package org.agrfesta.sh.api.core.domain.commons

import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.math.BigDecimal.ZERO

@JvmInline
value class Percentage(val value: BigDecimal) {
    init {
        require(value in ZERO..ONE) { "Percentage must be between 0 and 1, is $value." }
    }

    override fun toString(): String {
        var hundreds = value.movePointRight(2)
        if (hundreds.scale() > 0) hundreds = hundreds.stripTrailingZeros()
        return "$hundreds%"
    }

    companion object {
        private val HUNDRED = BigDecimal(100)
        fun of(percentage: String) = Percentage(BigDecimal(percentage))
        fun ofHundreds(value: BigDecimal): Percentage {
            require(value in ZERO..HUNDRED) { "Percentage hundreds must be between 0 and 100, is $value." }
            return Percentage(value.movePointLeft(2).stripTrailingZeros())
        }
        fun ofHundreds(value: Int) = ofHundreds(BigDecimal(value))
        fun ofHundreds(value: String) = ofHundreds(BigDecimal(value))
    }
}
