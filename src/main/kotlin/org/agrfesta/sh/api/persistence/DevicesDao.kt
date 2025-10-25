package org.agrfesta.sh.api.persistence

import java.util.*
import org.agrfesta.sh.api.domain.devices.DeviceDto
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceStatus

interface DevicesDao {

    /**
     * @param uuid [DeviceDto] unique identifier.
     * @return [DeviceDto] identified by [uuid].
     */
    fun getDeviceById(uuid: UUID): DeviceDto

    /**
     * @return All [DeviceDto]s.
     */
    fun getAll(): Collection<DeviceDto>

    /**
     * Persists a new [DeviceDto].
     *
     * @param device data.
     * @return the [UUID] of the persisted [device].
     */
    fun create(
        device: DeviceDataValue,
        initialStatus: DeviceStatus = DeviceStatus.PAIRED
    ): UUID

    /**
     * Update [DeviceDto] with [device] data.
     *
     * @param device data
     */
    fun update(device: DeviceDto)

}

class NotASensorException: Exception()
class NotAnActuatorException: Exception()
class DeviceNotFoundException: Exception()
