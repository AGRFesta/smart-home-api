package org.agrfesta.sh.api.providers.netatmo.devices

import arrow.core.Either
import java.util.*
import org.agrfesta.sh.api.domain.devices.OnOffActuator
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.domain.devices.Sensor
import org.agrfesta.sh.api.domain.devices.SensorReadings
import org.agrfesta.sh.api.domain.devices.SensorReadingsFailure

class NetatmoSmarther(
    override val uuid: UUID,
    override val provider: Provider,
    override val deviceProviderId: String,
): Sensor, OnOffActuator {

    override suspend fun fetchReadings(): Either<SensorReadingsFailure, SensorReadings> {
        TODO("Not yet implemented")
    }

    override fun on() {
        TODO("Not yet implemented")
    }

    override fun off() {
        TODO("Not yet implemented")
    }

}
