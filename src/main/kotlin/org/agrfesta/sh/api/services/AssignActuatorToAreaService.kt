package org.agrfesta.sh.api.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import java.util.UUID
import org.agrfesta.sh.api.core.application.ports.inbounds.AssignActuatorToAreaUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.ActuatorsAssignmentsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.failures.ActuatorAssignmentFailure
import org.agrfesta.sh.api.core.domain.failures.NotAnActuator
import org.springframework.stereotype.Service

@Service
class AssignActuatorToAreaService(
    private val areasRepository: AreasRepository,
    private val devicesRepository: DevicesRepository,
    private val actuatorsAssignmentsRepository: ActuatorsAssignmentsRepository
) : AssignActuatorToAreaUseCase {

    override fun execute(areaId: UUID, deviceId: UUID): Either<ActuatorAssignmentFailure, Unit> =
        areasRepository.getAreaById(areaId).flatMap { _ ->
            devicesRepository.getDeviceById(deviceId).flatMap { device ->
                if (device.isActuator()) {
                    actuatorsAssignmentsRepository.assign(areaId, deviceId)
                } else {
                    NotAnActuator(device.uuid, device.features).left()
                }
            }
        }

}
