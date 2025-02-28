package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import java.util.*
import org.agrfesta.sh.api.domain.Area
import org.agrfesta.sh.api.domain.failures.NotASensor
import org.agrfesta.sh.api.domain.failures.SensorAlreadyAssigned
import org.agrfesta.sh.api.domain.failures.SensorAssignmentFailure
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.failures.SameAreaAssignment
import org.agrfesta.sh.api.persistence.AssignmentSuccess
import org.agrfesta.sh.api.persistence.SensorsAssignmentsDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.AssignmentsJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.springframework.stereotype.Service

@Service
class SensorsAssignmentsDaoJdbcImpl(
    private val assignmentsJdbcRepository: AssignmentsJdbcRepository,
    private val devicesJdbcRepository: DevicesJdbcRepository
): SensorsAssignmentsDao {

    override fun assign(areaId: UUID, sensorId: UUID): Either<SensorAssignmentFailure, AssignmentSuccess> =
        devicesJdbcRepository.getDeviceById(sensorId).flatMap { entity ->
            if (!entity.asDevice().isSensor()) NotASensor.left()
            else {
                assignmentsJdbcRepository.findByDevice(sensorId).flatMap { assignments ->
                    val activeAssignments = assignments.filter { it.disconnectedOn == null }
                    if (activeAssignments.isNotEmpty()) {
                        val sameArea: Boolean = activeAssignments
                            .map { it.areaUuid }.contains(areaId)
                        if (sameArea) SameAreaAssignment.left() else SensorAlreadyAssigned.left()
                    } else {
                        assignmentsJdbcRepository.persistAssignment(areaId, sensorId)
                    }
                }
            }
        }

}
