package org.agrfesta.sh.api.providers.netatmo

import com.fasterxml.jackson.annotation.JsonProperty
import org.agrfesta.sh.api.domain.failures.ExceptionFailure

data class NetatmoRefreshTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String
)
data class NetatmoAuthFailure(override val exception: Exception): ExceptionFailure

object NetatmoSetStatusSuccess

data class NetatmoModule(
    val id: String,
    val type: String,
    val name: String,
    @JsonProperty("room_id") val roomId: String
)
