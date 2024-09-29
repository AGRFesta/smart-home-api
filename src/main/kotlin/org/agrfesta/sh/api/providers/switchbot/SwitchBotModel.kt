package org.agrfesta.sh.api.providers.switchbot

import com.fasterxml.jackson.annotation.JsonProperty
import org.agrfesta.sh.api.domain.commons.PercentageHundreds
import org.agrfesta.sh.api.domain.devices.BatteryValue
import org.agrfesta.sh.api.domain.devices.DeviceFeature
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.domain.devices.Humidity
import org.agrfesta.sh.api.domain.devices.HumidityValue
import org.agrfesta.sh.api.domain.devices.TemperatureValue
import java.math.BigDecimal

enum class SwitchBotDeviceType(val features: Set<DeviceFeature>) {
    @JsonProperty("MeterPlus") METER_PLUS(setOf(SENSOR)),
    @JsonProperty("Hub Mini") HUB_MINI(emptySet()),
    @JsonProperty("WoIOSensor") WO_IO_SENSOR(setOf(SENSOR)),
    @JsonProperty("Meter") METER(setOf(SENSOR))
}

data class SwitchBotSensorReadings(
    override val temperature: BigDecimal,
    val humidityInt: Int,
    override val battery: Int
): TemperatureValue, HumidityValue, BatteryValue {
    override val humidity: Humidity = PercentageHundreds(humidityInt).toPercentage()
}
