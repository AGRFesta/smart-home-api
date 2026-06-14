package org.agrfesta.sh.api.providers.netatmo.devices

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus.OFF
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus.ON
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus.UNDEFINED
import org.agrfesta.sh.api.core.domain.devices.Heater
import org.agrfesta.sh.api.core.domain.devices.Inspectable
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.domain.devices.SensorReadings
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderError
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderFailure
import org.agrfesta.sh.api.providers.netatmo.NETATMO_OBJECT_MAPPER
import org.agrfesta.sh.api.providers.netatmo.NetatmoClient
import org.agrfesta.sh.api.providers.netatmo.NetatmoClientFailure
import org.agrfesta.sh.api.providers.netatmo.NetatmoContractBreak
import org.agrfesta.sh.api.providers.netatmo.NetatmoHomeStatusChange
import org.agrfesta.sh.api.providers.netatmo.NetatmoRoomStatusChange
import org.agrfesta.sh.api.providers.netatmo.toException
import org.agrfesta.sh.api.utils.LoggerDelegate
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.*

class NetatmoSmarther(
    override val uuid: UUID,
    override val deviceProviderId: String,
    private val homeId: String,
    private val roomId: String,
    private val client: NetatmoClient,
    private val timeProvider: TimeProvider
) : Sensor, Heater, Inspectable {
    private val logger by LoggerDelegate()
    private val mapper = NETATMO_OBJECT_MAPPER
    override val provider = Provider.NETATMO

    // Ktor + Jackson parsing errors and room lookup must not escape the adapter as an unmapped 500
    @Suppress("TooGenericExceptionCaught")
    override fun inspect(): Either<DevicesProviderFailure, String> =
        runBlocking { client.getHomeStatusRaw(homeId) }
            .mapLeft { DevicesProviderError(it.toException()) }
            .flatMap { body ->
                try {
                    mapper.readTree(body).at("/body/home/rooms")
                        .first { it.get("id")?.asText() == roomId }
                        .toString()
                        .right()
                } catch (e: Exception) {
                    DevicesProviderError(e).left()
                }
            }

    companion object {
        internal val MIN_SET_POINT_TEMPERATURE: BigDecimal = BigDecimal("7")
        internal val MAX_SET_POINT_TEMPERATURE: BigDecimal = BigDecimal("30")
        internal const val SET_POINT_MODE = "manual"
    }

    override fun fetchReadings(): Either<NetatmoClientFailure, SensorReadings> {
        return runBlocking { client.getHomeStatus(homeId) }.flatMap { status ->
            when {
                status.rooms.size > 1 ->
                    NetatmoContractBreak("Not expecting to have more than one room").left()
                status.rooms.isEmpty() ->
                    NetatmoContractBreak("Not expecting to have no rooms").left()
                else ->
                    status.rooms.first().right()
            }
        }
    }

    override fun getActuatorStatus(): Either<NetatmoClientFailure, ActuatorStatus> =
        runBlocking { client.getHomeStatus(homeId) }.map {
            val room = it.rooms.first()
            val now = timeProvider.now()
            if (
                room.setPointMode == SET_POINT_MODE &&
                now.greaterEqualThan(room.setPointStartTime) &&
                now.lessEqualThan(room.setPointEndTime)
            ) {
                when {
                    room.setPointTemperature.compareTo(MAX_SET_POINT_TEMPERATURE) == 0 -> ON
                    room.setPointTemperature.compareTo(MIN_SET_POINT_TEMPERATURE) == 0 -> OFF
                    else -> UNDEFINED
                }
            } else { UNDEFINED }
        }.also {
            it.fold(
                ifLeft = { logger.error("NetatmoSmarther '$uuid' status fetch FAILED") },
                ifRight = { status -> logger.info("NetatmoSmarther '$uuid' is in status $status") }
            )
        }

    private fun Instant.greaterEqualThan(other: Long?) = other?.let { this.epochSecond >= it } ?: true

    private fun Instant.lessEqualThan(other: Long?) = other?.let { this.epochSecond <= it } ?: true

    override fun on(): Either<NetatmoClientFailure, Unit> {
        val status = NetatmoHomeStatusChange(
            id = homeId,
            rooms = listOf(
                NetatmoRoomStatusChange(
                    id = roomId,
                    setPointTemperature = MAX_SET_POINT_TEMPERATURE,
                    setPointMode = SET_POINT_MODE,
                    setPointEndTime = timeProvider.now().plus(Duration.ofHours(1)).epochSecond
                )
            )
        )
        return runBlocking { client.setState(status) }.map {}
    }

    override fun off(): Either<NetatmoClientFailure, Unit> {
        val status = NetatmoHomeStatusChange(
            id = homeId,
            rooms = listOf(
                NetatmoRoomStatusChange(
                    id = roomId,
                    setPointTemperature = MIN_SET_POINT_TEMPERATURE,
                    setPointMode = SET_POINT_MODE,
                    setPointEndTime = timeProvider.now().plus(Duration.ofHours(1)).epochSecond
                )
            )
        )
        return runBlocking { client.setState(status) }.map {}
    }
}
