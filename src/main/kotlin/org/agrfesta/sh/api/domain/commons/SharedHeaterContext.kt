package org.agrfesta.sh.api.domain.commons

import arrow.core.NonEmptySet
import org.agrfesta.sh.api.domain.areas.HeatableArea
import org.agrfesta.sh.api.domain.devices.Heater

data class SharedHeaterContext(
    val heater: Heater,
    val areas: NonEmptySet<HeatableArea>
) {
    init {
        val heaters = areas.map { it.heater }
        require(heaters.all { it == heater }) {
            "Areas are not sharing the same heater. Expected: $heater"
        }
    }
}
