package org.agrfesta.sh.api.providers.switchbot

import com.fasterxml.jackson.annotation.JsonProperty
import org.agrfesta.sh.api.domain.commons.PercentageHundreds
import org.agrfesta.sh.api.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.domain.devices.BatteryValue
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.domain.devices.SensorReadings
import org.agrfesta.sh.api.domain.devices.ThermoHygroDataValue
import java.math.BigDecimal

enum class SwitchBotDeviceType(val features: Set<DeviceFeature>) {
    @JsonProperty("MeterPlus") METER_PLUS(setOf(SENSOR)),
    @JsonProperty("Hub Mini") HUB_MINI(emptySet()),
    @JsonProperty("WoIOSensor") WO_IO_SENSOR(setOf(SENSOR)),
    @JsonProperty("Meter") METER(setOf(SENSOR))
}

data class SwitchBotSensorReadings(
    val temperature: BigDecimal,
    val humidityInt: Int,
    val batteryLevel: Int
) {

    fun toSensorReadings(): SensorReadings = object : ThermoHygroDataValue, BatteryValue {
        override val thermoHygroData = ThermoHygroData(
            temperature = temperature,
            relativeHumidity = PercentageHundreds(humidityInt).toPercentage()
        )
        override val battery: Int = batteryLevel
    }

}
