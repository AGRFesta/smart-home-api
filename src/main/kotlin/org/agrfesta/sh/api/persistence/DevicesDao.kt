package org.agrfesta.sh.api.persistence

import arrow.core.Either
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import java.util.*

interface DevicesDao {

    /**
     * @param uuid [Device] unique identifier.
     * @return [Device] identified by [uuid] or [GetDeviceFailure].
     */
    fun getDeviceById(uuid: UUID): Either<GetDeviceFailure, Device>

    /**
     * @return All [Device]s or [PersistenceFailure].
     */
    fun getAll(): Either<PersistenceFailure, Collection<Device>>

    /**
     * Persists a new [Device].
     *
     * @param device data.
     * @return the [UUID] of the persisted [device] or [PersistenceFailure].
     */
    fun create(
        device: DeviceDataValue,
        initialStatus: DeviceStatus = DeviceStatus.PAIRED
    ): Either<PersistenceFailure, UUID>

    /**
     * Update [Device] with [device] data.
     *
     * @param device data
     * @return [PersistenceFailure] or [PersistenceSuccess].
     */
    fun update(device: Device): Either<PersistenceFailure, PersistenceSuccess>

}

/**
 * Groups all causes of a failure fetching a [Device].
 */
sealed interface GetDeviceFailure: AssociationFailure

data object DeviceNotFound: GetDeviceFailure
