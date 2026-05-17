package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import org.agrfesta.sh.api.core.application.ports.inbounds.AssignActuatorToAreaUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.ActuatorsAssignmentsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.failures.ActuatorAssignmentFailure
import org.agrfesta.sh.api.core.domain.failures.AreaFetchFailure
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.AssignmentRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DeviceFetchFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.NotAnActuator
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AssignActuatorToAreaService(
    private val areasRepository: AreasRepository,
    private val devicesRepository: DevicesRepository,
    private val actuatorsAssignmentsRepository: ActuatorsAssignmentsRepository
) : AssignActuatorToAreaUseCase {

    override fun execute(areaId: UUID, deviceId: UUID): Either<ActuatorAssignmentFailure, Unit> =
        areasRepository.getAreaById(areaId)
            .mapLeft { it.toActuatorFailure() }
            .flatMap { _ ->
                devicesRepository.getDeviceById(deviceId)
                    .mapLeft { it.toActuatorFailure() }
                    .flatMap { device ->
                        if (device.isActuator()) {
                            actuatorsAssignmentsRepository.assign(areaId, deviceId)
                        } else {
                            NotAnActuator(device.uuid, device.features).left()
                        }
                    }
            }

    private fun AreaFetchFailure.toActuatorFailure(): ActuatorAssignmentFailure = when (this) {
        is AreaNotFound -> this
        AreaRepositoryError -> AssignmentRepositoryError
    }

    private fun DeviceFetchFailure.toActuatorFailure(): ActuatorAssignmentFailure = when (this) {
        is DeviceNotFound -> this
        DeviceRepositoryError -> AssignmentRepositoryError
    }
}
