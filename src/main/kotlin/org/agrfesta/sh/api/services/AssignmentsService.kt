package org.agrfesta.sh.api.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import java.util.*
import org.agrfesta.sh.api.domain.failures.ActuatorAssignmentFailure
import org.agrfesta.sh.api.domain.failures.NotASensor
import org.agrfesta.sh.api.domain.failures.NotAnActuator
import org.agrfesta.sh.api.domain.failures.SensorAssignmentFailure
import org.agrfesta.sh.api.persistence.ActuatorsAssignmentsDao
import org.agrfesta.sh.api.persistence.AreasDao
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.SensorsAssignmentsDao
import org.springframework.stereotype.Service

/**
 * Service responsible for assigning devices (sensors and actuators) to areas.
 *
 * Each assignment operation validates that both the target area and device exist,
 * and that the device is of the expected type.
 */
@Service
class AssignmentsService(
    private val areasDao: AreasDao,
    private val devicesDao: DevicesDao,
    private val sensorsAssignmentsDao: SensorsAssignmentsDao,
    private val actuatorsAssignmentsDao: ActuatorsAssignmentsDao
) {

    /**
     * Assigns a sensor device to the specified area.
     *
     * Fails if the area does not exist, the device does not exist, or the device is not a sensor.
     *
     * @param areaId The unique identifier of the target area.
     * @param deviceId The unique identifier of the device to assign.
     * @return [Either.Right] with [Unit] on success, or [Either.Left] with a [SensorAssignmentFailure].
     */
    fun assignSensorToArea(areaId: UUID, deviceId: UUID): Either<SensorAssignmentFailure, Unit> {
        return areasDao.getAreaById(areaId).flatMap { area ->
            devicesDao.getDeviceById(deviceId).flatMap { device ->
                if (device.isSensor()) {
                    sensorsAssignmentsDao.assign(areaId, deviceId)
                } else {
                    NotASensor(device.uuid, device.features).left()
                }
            }
        }
    }

    /**
     * Assigns an actuator device to the specified area.
     *
     * Fails if the area does not exist, the device does not exist, or the device is not an actuator.
     *
     * @param areaId The unique identifier of the target area.
     * @param deviceId The unique identifier of the device to assign.
     * @return [Either.Right] with [Unit] on success, or [Either.Left] with an [ActuatorAssignmentFailure].
     */
    fun assignActuatorToArea(areaId: UUID, deviceId: UUID): Either<ActuatorAssignmentFailure, Unit> {
        return areasDao.getAreaById(areaId).flatMap { _ ->
            devicesDao.getDeviceById(deviceId).flatMap { device ->
                if (device.isActuator()) {
                    actuatorsAssignmentsDao.assign(areaId, deviceId)
                } else {
                    NotAnActuator(device.uuid, device.features).left()
                }
            }
        }
    }

}
