package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.UUID
import org.agrfesta.sh.api.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.failures.ActuatorAssignmentFailure
import org.agrfesta.sh.api.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.failures.SameAreaAssignment
import org.agrfesta.sh.api.persistence.ActuatorsAssignmentsDao
import org.agrfesta.sh.api.persistence.AreaNotFoundException
import org.agrfesta.sh.api.persistence.DeviceNotFoundException
import org.agrfesta.sh.api.persistence.jdbc.repositories.ActuatorsAssignmentsJdbcRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

@Service
class ActuatorsAssignmentsDaoJdbcImpl(
    private val actuatorsAssignmentsJdbcRepository: ActuatorsAssignmentsJdbcRepository
): ActuatorsAssignmentsDao {

    override fun assign(areaId: UUID, actuatorId: UUID): Either<ActuatorAssignmentFailure, Unit> = try {
        val alreadyAssigned = actuatorsAssignmentsJdbcRepository.findByDevice(actuatorId)
            .map { it.areaUuid }.contains(areaId)
        if (alreadyAssigned) SameAreaAssignment.left()
        else actuatorsAssignmentsJdbcRepository.persistAssignment(areaId, actuatorId).right()
    } catch (_: DeviceNotFoundException) {
        DeviceNotFound(actuatorId).left()
    } catch (_: AreaNotFoundException) {
        AreaNotFound(areaId).left()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

}
