package org.agrfesta.sh.api.providers.netatmo

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.Instant
import org.agrfesta.sh.api.domain.commons.Percentage
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.domain.devices.ThermoHygroDataValue
import org.agrfesta.sh.api.domain.failures.ExceptionFailure
import org.agrfesta.sh.api.domain.failures.Failure

data class NetatmoRefreshTokenResponse(
    @field:JsonProperty("access_token") val accessToken: String,
    @field:JsonProperty("refresh_token") val refreshToken: String,
    @field:JsonProperty("expires_in") val expiresIn: Int
)
data class NetatmoAuthFailure(override val exception: Exception): ExceptionFailure

object NetatmoSetStatusSuccess

data class NetatmoContractBreak(val message: String, val response: String? = null): Failure

data class NetatmoHomeStatus(
    @field:JsonProperty("id") val id: String,
    @field:JsonProperty("rooms") val rooms: Collection<NetatmoRoomStatus>
)

data class NetatmoHomeStatusChange(
    @field:JsonProperty("id") val id: String,
    @field:JsonProperty("rooms") val rooms: Collection<NetatmoRoomStatusChange>
)

data class NetatmoStatusChangeRequest(
    @field:JsonProperty("home") val home: NetatmoHomeStatusChange
)

data class NetatmoRoomStatus(
    @field:JsonProperty("id") val id: String,
    @field:JsonProperty("humidity") val humidity: BigDecimal,
    @field:JsonProperty("open_window") val openWindow: Boolean,
    @field:JsonProperty("therm_measured_temperature") val measuredTemperature: Temperature,
    @field:JsonProperty("therm_setpoint_temperature") val setPointTemperature: Temperature,
    @field:JsonProperty("therm_setpoint_mode") val setPointMode: String,
    @field:JsonProperty("therm_setpoint_start_time") val setPointStartTime: Instant? = null,
    @field:JsonProperty("therm_setpoint_end_time") val setPointEndTime: Instant? = null
): ThermoHygroDataValue {
    override val thermoHygroData = ThermoHygroData(
        temperature = measuredTemperature,
        relativeHumidity = Percentage.ofHundreds(humidity)
    )
}

data class NetatmoRoomStatusChange(
    @field:JsonProperty("id") val id: String,
    @field:JsonProperty("therm_setpoint_temperature") val setPointTemperature: Temperature,
    @field:JsonProperty("therm_setpoint_mode") val setPointMode: String,
    @field:JsonProperty("therm_setpoint_end_time") val setPointEndTime: Instant
)

data class NetatmoModuleData(
    @field:JsonProperty("id") val id: String,
    @field:JsonProperty("type") val type: String,
    @field:JsonProperty("name") val name: String,
    @field:JsonProperty("room_id") val roomId: String
)
