package org.agrfesta.sh.api.providers.netatmo

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import org.agrfesta.sh.api.domain.commons.RelativeHumidityHundreds
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.domain.devices.ThermoHygroDataValue
import org.agrfesta.sh.api.domain.failures.ExceptionFailure
import org.agrfesta.sh.api.domain.failures.Failure

data class NetatmoRefreshTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String
)
data class NetatmoAuthFailure(override val exception: Exception): ExceptionFailure

object NetatmoSetStatusSuccess

data class NetatmoContractBreak(val message: String, val response: String? = null): Failure

data class NetatmoHomeStatus(
    val id: String,
    val rooms: Collection<NetatmoRoomStatus>
)

data class NetatmoHomeStatusChange(
    val id: String,
    val rooms: Collection<NetatmoRoomStatusChange>
)

data class NetatmoStatusChangeRequest(
    val home: NetatmoHomeStatusChange
)

data class NetatmoRoomStatus(
    val id: String,
    val humidity: RelativeHumidityHundreds,
    @JsonProperty("open_window") val openWindow: Boolean,
    @JsonProperty("therm_measured_temperature") val measuredTemperature: Temperature,
    @JsonProperty("therm_setpoint_temperature") val setPointTemperature: Temperature,
    @JsonProperty("therm_setpoint_mode") val setPointMode: String,
    @JsonProperty("therm_setpoint_start_time") val setPointStartTime: Instant? = null,
    @JsonProperty("therm_setpoint_end_time") val setPointEndTime: Instant? = null
): ThermoHygroDataValue {
    override val thermoHygroData = ThermoHygroData(
        temperature = measuredTemperature,
        relativeHumidity = humidity.toPercentage()
    )
}

data class NetatmoRoomStatusChange(
    val id: String,
    @JsonProperty("therm_setpoint_temperature") val setPointTemperature: Temperature,
    @JsonProperty("therm_setpoint_mode") val setPointMode: String,
    @JsonProperty("therm_setpoint_end_time") val setPointEndTime: Instant
)

data class NetatmoModuleData(
    val id: String,
    val type: String,
    val name: String,
    @JsonProperty("room_id") val roomId: String
)
