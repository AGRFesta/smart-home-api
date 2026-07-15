package org.agrfesta.sh.api.core.application.usecases.areas

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import org.agrfesta.sh.api.core.application.devices.DeviceModelCatalog
import org.agrfesta.sh.api.core.application.ports.inbounds.areas.AssignSensorToAreaUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.SensorsAssignmentsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.failures.AreaFetchFailure
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.AssignmentRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DeviceFetchFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.NotASensor
import org.agrfesta.sh.api.core.domain.failures.SensorAssignmentFailure
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AssignSensorToAreaService(
    private val areasRepository: AreasRepository,
    private val devicesRepository: DevicesRepository,
    private val sensorsAssignmentsRepository: SensorsAssignmentsRepository,
    private val catalog: DeviceModelCatalog
) : AssignSensorToAreaUseCase {

    override fun execute(areaId: UUID, deviceId: UUID): Either<SensorAssignmentFailure, Unit> =
        areasRepository.getAreaById(areaId)
            .mapLeft { it.toSensorFailure() }
            .flatMap { _ ->
                devicesRepository.getDeviceById(deviceId)
                    .mapLeft { it.toSensorFailure() }
                    .flatMap { device ->
                        val roles = catalog.rolesOf(device.model)
                        if (DeviceFeature.SENSOR in roles) {
                            sensorsAssignmentsRepository.assign(areaId, deviceId)
                        } else {
                            NotASensor(device.uuid, roles).left()
                        }
                    }
            }

    private fun AreaFetchFailure.toSensorFailure(): SensorAssignmentFailure = when (this) {
        is AreaNotFound -> this
        AreaRepositoryError -> AssignmentRepositoryError
    }

    private fun DeviceFetchFailure.toSensorFailure(): SensorAssignmentFailure = when (this) {
        is DeviceNotFound -> this
        DeviceRepositoryError -> AssignmentRepositoryError
    }
}
