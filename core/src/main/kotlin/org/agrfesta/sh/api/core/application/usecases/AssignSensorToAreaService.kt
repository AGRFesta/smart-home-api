package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import java.util.UUID
import org.agrfesta.sh.api.core.application.ports.inbounds.AssignSensorToAreaUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.SensorsAssignmentsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.failures.NotASensor
import org.agrfesta.sh.api.core.domain.failures.SensorAssignmentFailure
import org.springframework.stereotype.Service

@Service
class AssignSensorToAreaService(
    private val areasRepository: AreasRepository,
    private val devicesRepository: DevicesRepository,
    private val sensorsAssignmentsRepository: SensorsAssignmentsRepository
) : AssignSensorToAreaUseCase {

    override fun execute(areaId: UUID, deviceId: UUID): Either<SensorAssignmentFailure, Unit> =
        areasRepository.getAreaById(areaId).flatMap { _ ->
            devicesRepository.getDeviceById(deviceId).flatMap { device ->
                if (device.isSensor()) {
                    sensorsAssignmentsRepository.assign(areaId, deviceId)
                } else {
                    NotASensor(device.uuid, device.features).left()
                }
            }
        }

}
