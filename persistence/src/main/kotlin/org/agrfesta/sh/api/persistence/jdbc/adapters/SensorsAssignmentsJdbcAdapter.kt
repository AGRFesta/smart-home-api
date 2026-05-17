package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.SensorsAssignmentsRepository
import org.agrfesta.sh.api.core.domain.failures.AssignmentRepositoryError
import org.agrfesta.sh.api.core.domain.failures.SameAreaAssignment
import org.agrfesta.sh.api.core.domain.failures.SensorAlreadyAssigned
import org.agrfesta.sh.api.core.domain.failures.SensorAssignmentFailure
import org.agrfesta.sh.api.core.domain.failures.SensorNotAssigned
import org.agrfesta.sh.api.core.domain.failures.SensorUnassignFailure
import org.agrfesta.sh.api.persistence.AreaNotFoundException
import org.agrfesta.sh.api.persistence.DeviceNotFoundException
import org.agrfesta.sh.api.persistence.jdbc.repositories.SensorsAssignmentsJdbcRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SensorsAssignmentsJdbcAdapter(
    private val sensorsAssignmentsJdbcRepository: SensorsAssignmentsJdbcRepository
) : SensorsAssignmentsRepository {

    private val logger by LoggerDelegate()

    override fun assign(areaId: UUID, sensorId: UUID): Either<SensorAssignmentFailure, Unit> = try {
        val activeAssignments = sensorsAssignmentsJdbcRepository.findByDevice(sensorId)
            .filter { it.disconnectedOn == null }
        if (activeAssignments.isNotEmpty()) {
            val sameArea = activeAssignments.map { it.areaUuid }.contains(areaId)
            if (sameArea) { SameAreaAssignment.left() } else { SensorAlreadyAssigned.left() }
        } else {
            sensorsAssignmentsJdbcRepository.persistAssignment(areaId, sensorId).right()
        }
    } catch (e: DeviceNotFoundException) {
        logger.error("Unexpected FK violation: device '$sensorId' not found during assignment", e)
        AssignmentRepositoryError.left()
    } catch (e: AreaNotFoundException) {
        logger.error("Unexpected FK violation: area '$areaId' not found during assignment", e)
        AssignmentRepositoryError.left()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error during sensor assignment", e)
        AssignmentRepositoryError.left()
    }

    override fun unassign(areaId: UUID, sensorId: UUID): Either<SensorUnassignFailure, Unit> = try {
        sensorsAssignmentsJdbcRepository.findByDevice(sensorId)
            .filter { it.disconnectedOn == null && it.areaUuid == areaId }
            .firstOrNull()
            ?.let {
                sensorsAssignmentsJdbcRepository.disconnectSensor(areaId, sensorId)
                Unit.right()
            }
            ?: SensorNotAssigned.left()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error during sensor unassignment", e)
        AssignmentRepositoryError.left()
    }
}
