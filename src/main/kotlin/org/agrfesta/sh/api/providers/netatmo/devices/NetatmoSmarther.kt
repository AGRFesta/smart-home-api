package org.agrfesta.sh.api.providers.netatmo.devices

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import java.time.Duration
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.devices.OnOffActuator
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.Sensor
import org.agrfesta.sh.api.domain.devices.SensorReadings
import org.agrfesta.sh.api.domain.failures.Failure
import org.agrfesta.sh.api.providers.netatmo.NetatmoClient
import org.agrfesta.sh.api.providers.netatmo.NetatmoContractBreak
import org.agrfesta.sh.api.providers.netatmo.NetatmoHomeStatus
import org.agrfesta.sh.api.providers.netatmo.NetatmoHomeStatusChange
import org.agrfesta.sh.api.providers.netatmo.NetatmoRoomStatus
import org.agrfesta.sh.api.providers.netatmo.NetatmoRoomStatusChange
import org.agrfesta.sh.api.utils.TimeService

class NetatmoSmarther(
    override val uuid: UUID,
    override val deviceProviderId: String,
    private val homeId: String,
    private val roomId: String,
    private val client: NetatmoClient,
    private val timeService: TimeService
): Sensor, OnOffActuator {
    override val provider = Provider.NETATMO

    companion object {
        private val MIN_SET_POINT_TEMPERATURE = Temperature("7.0")
        private val MAX_SET_POINT_TEMPERATURE = Temperature("40.0")
        private const val SET_POINT_MODE = "manual"
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

    override suspend fun on(): Either<Failure, Unit> {
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

    override suspend fun off(): Either<Failure, Unit> {
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

}
