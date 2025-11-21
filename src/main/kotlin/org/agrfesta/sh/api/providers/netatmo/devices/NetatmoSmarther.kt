package org.agrfesta.sh.api.providers.netatmo.devices

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import java.time.Duration
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.domain.devices.ActuatorStatus.OFF
import org.agrfesta.sh.api.domain.devices.ActuatorStatus.ON
import org.agrfesta.sh.api.domain.devices.ActuatorStatus.UNDEFINED
import org.agrfesta.sh.api.domain.devices.Heater
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.Sensor
import org.agrfesta.sh.api.domain.devices.SensorReadings
import org.agrfesta.sh.api.domain.failures.Failure
import org.agrfesta.sh.api.providers.netatmo.NetatmoClient
import org.agrfesta.sh.api.providers.netatmo.NetatmoContractBreak
import org.agrfesta.sh.api.providers.netatmo.NetatmoHomeStatusChange
import org.agrfesta.sh.api.providers.netatmo.NetatmoRoomStatusChange
import org.agrfesta.sh.api.utils.TimeService

class NetatmoSmarther(
    override val uuid: UUID,
    override val deviceProviderId: String,
    private val homeId: String,
    private val roomId: String,
    private val client: NetatmoClient,
    private val timeService: TimeService
): Sensor, Heater {
    override val provider = Provider.NETATMO

    companion object {
        val MIN_SET_POINT_TEMPERATURE = Temperature("7.0")
        val MAX_SET_POINT_TEMPERATURE = Temperature("40.0")
        const val SET_POINT_MODE = "manual"
    }

    override suspend fun fetchReadings(): Either<Failure, SensorReadings> {
        return client.getHomeStatus(homeId).flatMap { status ->
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

    override suspend fun getActuatorStatus(): Either<Failure, ActuatorStatus> = client.getHomeStatus(homeId).map {
        val room = it.rooms.first()
        val now = timeService.now()
        if (room.setPointMode == SET_POINT_MODE
            && now.greaterEqualThan(room.setPointStartTime) && now.lessEqualThan(room.setPointEndTime)) {
            when(room.setPointTemperature) {
                MAX_SET_POINT_TEMPERATURE -> ON
                MIN_SET_POINT_TEMPERATURE -> OFF
                else -> UNDEFINED
            }
        } else UNDEFINED
    }

    private fun Instant.greaterEqualThan(other: Instant?) = other?.let { this >= it } ?: true

    private fun Instant.lessEqualThan(other: Instant?) = other?.let { this <= it } ?: true

    override suspend fun on(): Either<Failure, Unit> {
        val status = NetatmoHomeStatusChange(
            id = homeId,
            rooms = listOf(
                NetatmoRoomStatusChange(
                    id = roomId,
                    setPointTemperature = MAX_SET_POINT_TEMPERATURE,
                    setPointMode = SET_POINT_MODE,
                    setPointEndTime = timeService.now().plus(Duration.ofHours(1))
                )
            )
        )
        return client.setState(status).map {}
    }

    override suspend fun off(): Either<Failure, Unit> {
        val status = NetatmoHomeStatusChange(
            id = homeId,
            rooms = listOf(
                NetatmoRoomStatusChange(
                    id = roomId,
                    setPointTemperature = MIN_SET_POINT_TEMPERATURE,
                    setPointMode = SET_POINT_MODE,
                    setPointEndTime = timeService.now().plus(Duration.ofHours(1))
                )
            )
        )
        return client.setState(status).map {}
    }

}
