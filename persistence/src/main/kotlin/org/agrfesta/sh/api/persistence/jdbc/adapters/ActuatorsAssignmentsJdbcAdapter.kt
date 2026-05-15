package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.UUID
import org.agrfesta.sh.api.core.domain.failures.ActuatorAssignmentFailure
import org.agrfesta.sh.api.core.domain.failures.AssignmentRepositoryError
import org.agrfesta.sh.api.core.domain.failures.SameAreaAssignment
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.ActuatorsAssignmentsRepository
import org.agrfesta.sh.api.persistence.AreaNotFoundException
import org.agrfesta.sh.api.persistence.DeviceNotFoundException
import org.agrfesta.sh.api.persistence.jdbc.repositories.ActuatorsAssignmentsJdbcRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

@Service
class ActuatorsAssignmentsJdbcAdapter(
    private val actuatorsAssignmentsJdbcRepository: ActuatorsAssignmentsJdbcRepository
): ActuatorsAssignmentsRepository {

    private val logger by LoggerDelegate()

    override fun assign(areaId: UUID, actuatorId: UUID): Either<ActuatorAssignmentFailure, Unit> = try {
        val alreadyAssigned = actuatorsAssignmentsJdbcRepository.findByDevice(actuatorId)
            .map { it.areaUuid }.contains(areaId)
        if (alreadyAssigned) SameAreaAssignment.left()
        else actuatorsAssignmentsJdbcRepository.persistAssignment(areaId, actuatorId).right()
    } catch (e: DeviceNotFoundException) {
        logger.error("Unexpected FK violation: device '$actuatorId' not found during assignment", e)
        AssignmentRepositoryError.left()
    } catch (e: AreaNotFoundException) {
        logger.error("Unexpected FK violation: area '$areaId' not found during assignment", e)
        AssignmentRepositoryError.left()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error during actuator assignment", e)
        AssignmentRepositoryError.left()
    }

}
