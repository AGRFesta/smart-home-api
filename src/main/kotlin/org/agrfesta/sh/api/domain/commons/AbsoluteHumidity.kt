package org.agrfesta.sh.api.domain.commons

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class AbsoluteHumidity(temperature: Temperature, relativeHumidity: RelativeHumidityHundreds) {
    val value: BigDecimal

    companion object {
        private val a = BigDecimal("6.112") // Constant factor
        private val b = BigDecimal("17.67") // Factor for the exponent
        private val c = BigDecimal("243.5") // Temperature adjustment constant
        private val conversionFactor = BigDecimal("2.1674") // Simplified conversion factor
        private val shiftForKelvin = BigDecimal("273.15")
        private val mc = MathContext.DECIMAL128
        private const val SCALE = 5
    }

    init {
        val temperatureKelvin = temperature.add(shiftForKelvin)

        // Calculate the exponent in the saturation vapor pressure formula
        val exponent = b.multiply(temperature).divide(temperature.add(c), mc)

        val expResult = expBigDecimal(exponent)
        // Calculate saturation vapor pressure
        val saturationVaporPressure = a.multiply(expResult)

        // Calculate absolute humidity (AH)
        val absoluteHumidity = saturationVaporPressure
            .multiply(relativeHumidity.value)
            .multiply(conversionFactor)
            .divide(temperatureKelvin, mc)

        value = absoluteHumidity.setScale(SCALE, RoundingMode.HALF_UP)
    }

    // Function to calculate e^x for BigDecimal using a Taylor series approximation
    private fun expBigDecimal(x: BigDecimal): BigDecimal {
        var result = BigDecimal.ONE
        var term = BigDecimal.ONE
        var n = BigDecimal.ONE
        val precision = mc.precision
        for (i in 1..precision * 2) { // Use higher number of iterations for precision
            term = term.multiply(x).divide(n, mc)
            result = result.add(term, mc)
            n = n.add(BigDecimal.ONE)
            if (term.abs() < BigDecimal.ONE.scaleByPowerOfTen(-precision)) break // Stop if term is small enough
        }
        return result
    }
}
