package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.inbounds.SnapshotSensorHistoryUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsCurrentReadingsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.sensors.SensorsHistoryDataRepository
import org.agrfesta.sh.api.core.domain.failures.SnapshotSensorHistoryError
import org.agrfesta.sh.api.core.domain.failures.SnapshotSensorHistoryFailure
import org.springframework.stereotype.Service

@Service
class SnapshotSensorHistoryService(
    private val devicesRepository: DevicesRepository,
    private val readingsRepository: SensorsCurrentReadingsRepository,
    private val historyRepository: SensorsHistoryDataRepository,
    private val timeProvider: TimeProvider
) : SnapshotSensorHistoryUseCase {

    override fun execute(): Either<SnapshotSensorHistoryFailure, Unit> {
        val devices = devicesRepository.getAll()
            .mapLeft { SnapshotSensorHistoryError }
            .getOrElse { return it.left() }
        val now by lazy { timeProvider.now() }
        devices
            .filter { it.isSensor() }
            .forEach { sensor ->
                val readings = readingsRepository.findBy(sensor).getOrElse { return@forEach } ?: return@forEach
                historyRepository.persistTemperature(sensor.uuid, now, readings.temperature)
                historyRepository.persistHumidity(sensor.uuid, now, readings.relativeHumidity)
            }
        return Unit.right()
    }
}
