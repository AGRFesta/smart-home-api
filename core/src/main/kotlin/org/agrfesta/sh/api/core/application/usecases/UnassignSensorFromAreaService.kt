package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import java.util.UUID
import org.agrfesta.sh.api.core.application.ports.inbounds.UnassignSensorFromAreaUseCase
import arrow.core.flatMap
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.SensorsAssignmentsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.failures.AreaFetchFailure
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.AssignmentRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DeviceFetchFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.SensorUnassignFailure
import org.springframework.stereotype.Service

@Service
class UnassignSensorFromAreaService(
    private val areasRepository: AreasRepository,
    private val devicesRepository: DevicesRepository,
    private val sensorsAssignmentsRepository: SensorsAssignmentsRepository
) : UnassignSensorFromAreaUseCase {

    override fun execute(areaId: UUID, deviceId: UUID): Either<SensorUnassignFailure, Unit> =
        areasRepository.getAreaById(areaId)
            .mapLeft { it.toSensorUnassignFailure() }
            .flatMap { _ ->
                devicesRepository.getDeviceById(deviceId)
                    .mapLeft { it.toSensorUnassignFailure() }
                    .flatMap { _ -> sensorsAssignmentsRepository.unassign(areaId, deviceId) }
            }

    private fun AreaFetchFailure.toSensorUnassignFailure(): SensorUnassignFailure = when (this) {
        is AreaNotFound -> this
        AreaRepositoryError -> AssignmentRepositoryError
    }

    private fun DeviceFetchFailure.toSensorUnassignFailure(): SensorUnassignFailure = when (this) {
        is DeviceNotFound -> this
        DeviceRepositoryError -> AssignmentRepositoryError
    }

}
