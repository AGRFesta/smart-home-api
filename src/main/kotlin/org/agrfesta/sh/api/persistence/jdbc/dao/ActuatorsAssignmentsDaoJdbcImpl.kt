package org.agrfesta.sh.api.persistence.jdbc.dao

import java.util.*
import org.agrfesta.sh.api.persistence.ActuatorsAssignmentsDao
import org.agrfesta.sh.api.persistence.NotAnActuatorException
import org.agrfesta.sh.api.persistence.SameAreaAssignmentException
import org.agrfesta.sh.api.persistence.jdbc.repositories.ActuatorsAssignmentsJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.springframework.stereotype.Service

@Service
class ActuatorsAssignmentsDaoJdbcImpl(
    private val actuatorsAssignmentsJdbcRepository: ActuatorsAssignmentsJdbcRepository,
    private val devicesJdbcRepository: DevicesJdbcRepository
): ActuatorsAssignmentsDao {

    override fun assign(areaId: UUID, actuatorId: UUID) {
        val entity = devicesJdbcRepository.getDeviceById(actuatorId)
        if (!entity.asDevice().isActuator()) {
            throw NotAnActuatorException()
        } else {
            val assignments = actuatorsAssignmentsJdbcRepository.findByDevice(actuatorId)
            if (assignments.map { it.areaUuid }.contains(areaId)) {
                throw SameAreaAssignmentException()
            } else {
                actuatorsAssignmentsJdbcRepository.persistAssignment(areaId, actuatorId)
            }
        }
    }

}
