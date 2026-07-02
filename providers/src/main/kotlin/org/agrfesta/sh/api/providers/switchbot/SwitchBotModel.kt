package org.agrfesta.sh.api.providers.switchbot

import com.fasterxml.jackson.annotation.JsonProperty
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.core.domain.devices.SensorReadings
import org.agrfesta.sh.api.core.domain.devices.ThermoHygroDataValue

enum class SwitchBotDeviceType(val model: String) {
    @JsonProperty("MeterPlus") METER_PLUS("switchbot/MeterPlus"),
    @JsonProperty("Hub Mini") HUB_MINI("switchbot/Hub Mini"),
    @JsonProperty("WoIOSensor") WO_IO_SENSOR("switchbot/WoIOSensor"),
    @JsonProperty("Meter") METER("switchbot/Meter")
}

data class SwitchBotSensorReadings(
    val temperature: Temperature,
    val humidityInt: Int
) {

    fun toSensorReadings(): SensorReadings = object : ThermoHygroDataValue {
        override val thermoHygroData = ThermoHygroData(
            temperature = temperature,
            relativeHumidity = Percentage.ofHundreds(humidityInt)
        )
    }
}
