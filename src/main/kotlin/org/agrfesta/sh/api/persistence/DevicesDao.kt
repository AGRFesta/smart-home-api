package org.agrfesta.sh.api.persistence

import java.util.*
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceStatus

interface DevicesDao {

    /**
     * @param uuid [Device] unique identifier.
     * @return [Device] identified by [uuid].
     */
    fun getDeviceById(uuid: UUID): Device

    /**
     * @return All [Device]s.
     */
    fun getAll(): Collection<Device>

    /**
     * Persists a new [Device].
     *
     * @param device data.
     * @return the [UUID] of the persisted [device].
     */
    fun create(
        device: DeviceDataValue,
        initialStatus: DeviceStatus = DeviceStatus.PAIRED
    ): UUID

    /**
     * Update [Device] with [device] data.
     *
     * @param device data
     */
    fun update(device: Device)

}

class NotASensorException: Exception()
class NotAnActuatorException: Exception()
class DeviceNotFoundException: Exception()
