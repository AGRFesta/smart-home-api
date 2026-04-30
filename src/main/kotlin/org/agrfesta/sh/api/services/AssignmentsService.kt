package org.agrfesta.sh.api.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import java.util.*
import org.agrfesta.sh.api.core.domain.failures.ActuatorAssignmentFailure
import org.agrfesta.sh.api.core.domain.failures.NotASensor
import org.agrfesta.sh.api.core.domain.failures.NotAnActuator
import org.agrfesta.sh.api.core.domain.failures.SensorAssignmentFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.ActuatorsAssignmentsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.SensorsAssignmentsRepository
import org.springframework.stereotype.Service

/**
 * Service responsible for assigning devices (sensors and actuators) to areas.
 *
 * Each assignment operation validates that both the target area and device exist,
 * and that the device is of the expected type.
 */
@Service
class AssignmentsService(
    private val areasRepository: AreasRepository,
    private val devicesRepository: DevicesRepository,
    private val sensorsAssignmentsRepository: SensorsAssignmentsRepository,
    private val actuatorsAssignmentsRepository: ActuatorsAssignmentsRepository
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
        return areasRepository.getAreaById(areaId).flatMap { area ->
            devicesRepository.getDeviceById(deviceId).flatMap { device ->
                if (device.isSensor()) {
                    sensorsAssignmentsRepository.assign(areaId, deviceId)
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
        return areasRepository.getAreaById(areaId).flatMap { _ ->
            devicesRepository.getDeviceById(deviceId).flatMap { device ->
                if (device.isActuator()) {
                    actuatorsAssignmentsRepository.assign(areaId, deviceId)
                } else {
                    NotAnActuator(device.uuid, device.features).left()
                }
            }
        }
    }

}
