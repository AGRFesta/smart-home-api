package org.agrfesta.sh.api.domain.commons

import java.math.BigDecimal
import java.math.RoundingMode

typealias Temperature = BigDecimal
typealias RelativeHumidity = Percentage
typealias RelativeHumidityHundreds = PercentageHundreds

fun Collection<Temperature>.average(): Temperature? {
    if (isEmpty()) return null
    val average = fold(BigDecimal.ZERO) { acc, num -> acc + num }
        .divide(BigDecimal(size), 2, RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()
    return BigDecimal(average)
}
