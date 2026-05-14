package org.agrfesta.sh.api.providers.netatmo.devices

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.domain.commons.Temperature.Companion.of
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus.OFF
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus.ON
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus.UNDEFINED
import org.agrfesta.sh.api.core.domain.devices.Heater
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.Sensor
import org.agrfesta.sh.api.core.domain.devices.SensorReadings
import org.agrfesta.sh.api.providers.netatmo.NetatmoClient
import org.agrfesta.sh.api.providers.netatmo.NetatmoClientFailure
import org.agrfesta.sh.api.providers.netatmo.NetatmoContractBreak
import org.agrfesta.sh.api.providers.netatmo.NetatmoHomeStatusChange
import org.agrfesta.sh.api.providers.netatmo.NetatmoRoomStatusChange
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider

class NetatmoSmarther(
    override val uuid: UUID,
    override val deviceProviderId: String,
    private val homeId: String,
    private val roomId: String,
    private val client: NetatmoClient,
    private val timeProvider: TimeProvider
): Sensor, Heater {
    private val logger by LoggerDelegate()
    override val provider = Provider.NETATMO

    companion object {
        internal val MIN_SET_POINT_TEMPERATURE = of("7")
        internal val MAX_SET_POINT_TEMPERATURE = of("30")
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

    override fun getActuatorStatus(): Either<NetatmoClientFailure, ActuatorStatus> = runBlocking { client.getHomeStatus(homeId) }.map {
        val room = it.rooms.first()
        val now = timeProvider.now()
        if (room.setPointMode == SET_POINT_MODE
            && now.greaterEqualThan(room.setPointStartTime) && now.lessEqualThan(room.setPointEndTime)) {
            when(room.setPointTemperature) {
                MAX_SET_POINT_TEMPERATURE -> ON
                MIN_SET_POINT_TEMPERATURE -> OFF
                else -> UNDEFINED
            }
        } else UNDEFINED
    }.also {
        it.fold(
            ifLeft = { logger.error("NetatmoSmarther '$uuid' status fetch FAILED") },
            ifRight = { status -> logger.info("NetatmoSmarther '$uuid' is in status $status") }
        )
    }

    private fun Instant.greaterEqualThan(other: Instant?) = other?.let { this >= it } ?: true

    private fun Instant.lessEqualThan(other: Instant?) = other?.let { this <= it } ?: true

    override fun on(): Either<NetatmoClientFailure, Unit> {
        val status = NetatmoHomeStatusChange(
            id = homeId,
            rooms = listOf(
                NetatmoRoomStatusChange(
                    id = roomId,
                    setPointTemperature = MAX_SET_POINT_TEMPERATURE,
                    setPointMode = SET_POINT_MODE,
                    setPointEndTime = timeProvider.now().plus(Duration.ofHours(1))
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
                    setPointEndTime = timeProvider.now().plus(Duration.ofHours(1))
                )
            )
        )
        return runBlocking { client.setState(status) }.map {}
    }

}
