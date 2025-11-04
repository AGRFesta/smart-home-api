package org.agrfesta.sh.api.providers.netatmo

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import org.agrfesta.sh.api.domain.failures.ExceptionFailure

data class NetatmoRefreshTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String
)
data class NetatmoAuthFailure(override val exception: Exception): ExceptionFailure

object NetatmoSetStatusSuccess

data class NetatmoHomeStatus(
    val id: String,
    val rooms: Collection<NetatmoRoomStatus>
)

data class NetatmoRoomStatus(
    val id: String,
    val humidity: Int,
    @JsonProperty("open_window") val openWindow: Boolean,
    @JsonProperty("therm_measured_temperature") val measuredTemperature: Float,
    @JsonProperty("therm_setpoint_temperature") val setpointTemperature: Float,
    @JsonProperty("therm_setpoint_mode") val setpointMode: String,
    @JsonProperty("therm_setpoint_start_time") val setpointStartTime: Instant? = null,
    @JsonProperty("therm_setpoint_end_time") val setpointEndTime: Instant? = null
)

data class NetatmoModuleData(
    val id: String,
    val type: String,
    val name: String,
    @JsonProperty("room_id") val roomId: String
)
