package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.UUID
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.domain.failures.SameAreaAssignment
import org.agrfesta.sh.api.core.domain.failures.SensorAlreadyAssigned
import org.agrfesta.sh.api.core.domain.failures.SensorAssignmentFailure
import org.agrfesta.sh.api.persistence.AreaNotFoundException
import org.agrfesta.sh.api.core.application.ports.outbounds.SensorsAssignmentsRepository
import org.agrfesta.sh.api.persistence.DeviceNotFoundException
import org.agrfesta.sh.api.persistence.jdbc.repositories.SensorsAssignmentsJdbcRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

@Service
class SensorsAssignmentsJdbcAdapter(
    private val sensorsAssignmentsJdbcRepository: SensorsAssignmentsJdbcRepository
): SensorsAssignmentsRepository {

    override fun assign(areaId: UUID, sensorId: UUID): Either<SensorAssignmentFailure, Unit> = try {
        val activeAssignments = sensorsAssignmentsJdbcRepository.findByDevice(sensorId)
            .filter { it.disconnectedOn == null }
        if (activeAssignments.isNotEmpty()) {
            val sameArea = activeAssignments.map { it.areaUuid }.contains(areaId)
            if (sameArea) SameAreaAssignment.left()
            else SensorAlreadyAssigned.left()
        } else {
            sensorsAssignmentsJdbcRepository.persistAssignment(areaId, sensorId).right()
        }
    } catch (_: DeviceNotFoundException) {
        DeviceNotFound(sensorId).left()
    } catch (_: AreaNotFoundException) {
        AreaNotFound(areaId).left()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

}
