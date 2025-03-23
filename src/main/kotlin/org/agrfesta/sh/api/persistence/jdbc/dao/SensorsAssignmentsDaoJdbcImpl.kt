package org.agrfesta.sh.api.persistence.jdbc.dao

import java.util.*
import org.agrfesta.sh.api.persistence.NotASensorException
import org.agrfesta.sh.api.persistence.SameAreaAssignmentException
import org.agrfesta.sh.api.persistence.SensorAlreadyAssignedException
import org.agrfesta.sh.api.persistence.SensorsAssignmentsDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.SensorsAssignmentsJdbcRepository
import org.springframework.stereotype.Service

@Service
class SensorsAssignmentsDaoJdbcImpl(
    private val sensorsAssignmentsJdbcRepository: SensorsAssignmentsJdbcRepository,
    private val devicesJdbcRepository: DevicesJdbcRepository
): SensorsAssignmentsDao {

    override fun assign(areaId: UUID, sensorId: UUID) {
        val entity = devicesJdbcRepository.getDeviceById(sensorId)
        if (!entity.asDevice().isSensor()) throw NotASensorException()
        val assignments = sensorsAssignmentsJdbcRepository.findByDevice(sensorId)
        val activeAssignments = assignments.filter { it.disconnectedOn == null }
        if (activeAssignments.isNotEmpty()) {
            val sameArea: Boolean = activeAssignments
                .map { it.areaUuid }.contains(areaId)
            if (sameArea) throw SameAreaAssignmentException()
            throw SensorAlreadyAssignedException()
        }
        sensorsAssignmentsJdbcRepository.persistAssignment(areaId, sensorId)
    }

}
