package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import java.util.*
import org.agrfesta.sh.api.domain.failures.ActuatorAssignmentFailure
import org.agrfesta.sh.api.domain.failures.NotAnActuator
import org.agrfesta.sh.api.domain.failures.SameAreaAssignment
import org.agrfesta.sh.api.persistence.ActuatorsAssignmentsDao
import org.agrfesta.sh.api.persistence.AssignmentSuccess
import org.agrfesta.sh.api.persistence.jdbc.repositories.ActuatorsAssignmentsJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.springframework.stereotype.Service

@Service
class ActuatorsAssignmentsDaoJdbcImpl(
    private val actuatorsAssignmentsJdbcRepository: ActuatorsAssignmentsJdbcRepository,
    private val devicesJdbcRepository: DevicesJdbcRepository
): ActuatorsAssignmentsDao {

    override fun assign(areaId: UUID, actuatorId: UUID): Either<ActuatorAssignmentFailure, AssignmentSuccess> {
        return devicesJdbcRepository.getDeviceById(actuatorId).flatMap { entity ->
            if (!entity.asDevice().isActuator()) NotAnActuator.left() else {
                actuatorsAssignmentsJdbcRepository.findByDevice(actuatorId).flatMap { assignments ->
                    if (assignments.map { it.areaUuid }.contains(areaId)) {
                        SameAreaAssignment.left()
                    } else {
                        actuatorsAssignmentsJdbcRepository.persistAssignment(areaId, actuatorId)
                    }
                }
            }
        }
    }

}
