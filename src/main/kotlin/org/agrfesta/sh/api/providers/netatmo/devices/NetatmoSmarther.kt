package org.agrfesta.sh.api.providers.netatmo.devices

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import java.util.*
import org.agrfesta.sh.api.domain.devices.OnOffActuator
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.Sensor
import org.agrfesta.sh.api.domain.devices.SensorReadings
import org.agrfesta.sh.api.domain.failures.Failure
import org.agrfesta.sh.api.providers.netatmo.NetatmoClient
import org.agrfesta.sh.api.providers.netatmo.NetatmoContractBreak

class NetatmoSmarther(
    override val uuid: UUID,
    override val deviceProviderId: String,
    private val homeId: String,
    private val client: NetatmoClient
): Sensor, OnOffActuator {
    override val provider = Provider.NETATMO

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

    override fun on() {
        TODO("Not yet implemented")
    }

    override fun off() {
        TODO("Not yet implemented")
    }

}
