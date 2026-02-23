package org.agrfesta.sh.api.domain.commons

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
@JsonSerialize(using = TemperatureSerializer::class)
@JsonDeserialize(using = TemperatureDeserializer::class)
value class Temperature(val value: BigDecimal) : Comparable<Temperature> {
    
    constructor(temperature: String) : this(
        BigDecimal(temperature).stripTrailingZeros()
    )
    
    constructor(temperature: Double) : this(
        BigDecimal.valueOf(temperature).stripTrailingZeros()
    )
    
    constructor(temperature: Int) : this(
        BigDecimal(temperature).stripTrailingZeros()
    )
    
    companion object {
        private const val DIVISION_SCALE = 10
        
        operator fun invoke(temperature: BigDecimal): Temperature = 
            Temperature(temperature.stripTrailingZeros())
    }
    
    override operator fun compareTo(other: Temperature): Int = 
        this.value.compareTo(other.value)
    
    operator fun plus(other: Temperature): Temperature = 
        Temperature(this.value + other.value)
    
    operator fun minus(other: Temperature): Temperature = 
        Temperature(this.value - other.value)
    
    operator fun times(other: Temperature): Temperature = 
        Temperature(this.value * other.value)
    
    operator fun times(multiplier: BigDecimal): Temperature = 
        Temperature(this.value * multiplier)
    
    operator fun div(other: Temperature): Temperature = 
        Temperature(this.value.divide(other.value, DIVISION_SCALE, RoundingMode.HALF_UP).stripTrailingZeros())
    
    operator fun div(divisor: BigDecimal): Temperature = 
        Temperature(this.value.divide(divisor, DIVISION_SCALE, RoundingMode.HALF_UP).stripTrailingZeros())
    
    operator fun unaryMinus(): Temperature = 
        Temperature(-this.value)
    
    override fun toString(): String = value.toPlainString()
}

typealias RelativeHumidity = Percentage
typealias RelativeHumidityHundreds = PercentageHundreds

fun Collection<Temperature>.average(): Temperature? {
    if (isEmpty()) return null
    val average = fold(BigDecimal.ZERO) { acc, num -> acc + num.value }
        .divide(BigDecimal(size), 2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
    return Temperature(average)
}
