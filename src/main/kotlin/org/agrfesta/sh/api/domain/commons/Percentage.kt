package org.agrfesta.sh.api.domain.commons

import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.math.BigDecimal.ZERO

data class Percentage(val value: BigDecimal) {

    init {
        require(value >= ZERO && value <= ONE) { "Percentage must be between 0 and 1, is $value." }
    }

    fun asText(): String = value.toString()
    fun toHundreds(): PercentageHundreds {
        var hundreds = value.movePointRight(2)
        if (hundreds.scale() > 0) {
            hundreds = hundreds.stripTrailingZeros()
        }
        return PercentageHundreds(hundreds)
    }

    override fun toString(): String = toHundreds().toString()
}

data class PercentageHundreds(val value: BigDecimal) {
    constructor(hundredPercentage: Int): this(BigDecimal(hundredPercentage))

    companion object {
        private val HUNDRED = BigDecimal(100)
    }

    init {
        require(value >= ZERO && value <= HUNDRED) { "Percentage hundreds must be between 0 and 100, is $value." }
    }

    fun toPercentage() = Percentage(value.movePointLeft(2).stripTrailingZeros())

    override fun toString(): String = "$value%"
}
