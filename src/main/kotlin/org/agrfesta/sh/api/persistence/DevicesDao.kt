package org.agrfesta.sh.api.persistence

import arrow.core.Either
import org.agrfesta.sh.api.domain.devices.Device
import java.util.*

interface DevicesDao {
    fun getDeviceById(uuid: UUID): Either<GetDeviceFailure, Device>
    fun getAll(): Collection<Device> //TODO should handle failures

    /**
     * Persists a new [Device].
     *
     * @param device data.
     * @return the [UUID] of the persisted [device].
     */
    fun create(device: Device): UUID //TODO should handle failures

    fun update(device: Device) //TODO should handle failures
}

sealed interface GetDeviceFailure: AssociationFailure

data object DeviceNotFound: GetDeviceFailure
