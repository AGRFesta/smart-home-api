package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.flatMap
import org.agrfesta.sh.api.core.application.ports.inbounds.UnassignActuatorFromAreaUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.ActuatorsAssignmentsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.failures.ActuatorUnassignFailure
import org.agrfesta.sh.api.core.domain.failures.AreaFetchFailure
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.AssignmentRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DeviceFetchFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UnassignActuatorFromAreaService(
    private val areasRepository: AreasRepository,
    private val devicesRepository: DevicesRepository,
    private val actuatorsAssignmentsRepository: ActuatorsAssignmentsRepository
) : UnassignActuatorFromAreaUseCase {

    override fun execute(areaId: UUID, deviceId: UUID): Either<ActuatorUnassignFailure, Unit> =
        areasRepository.getAreaById(areaId)
            .mapLeft { it.toActuatorUnassignFailure() }
            .flatMap { _ -> devicesRepository.getDeviceById(deviceId).mapLeft { it.toActuatorUnassignFailure() } }
            .flatMap { _ -> actuatorsAssignmentsRepository.unassign(areaId, deviceId) }

    private fun AreaFetchFailure.toActuatorUnassignFailure(): ActuatorUnassignFailure = when (this) {
        is AreaNotFound -> this
        AreaRepositoryError -> AssignmentRepositoryError
    }

    private fun DeviceFetchFailure.toActuatorUnassignFailure(): ActuatorUnassignFailure = when (this) {
        is DeviceNotFound -> this
        DeviceRepositoryError -> AssignmentRepositoryError
    }
}
