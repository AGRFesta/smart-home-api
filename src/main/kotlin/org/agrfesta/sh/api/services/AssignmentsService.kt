package org.agrfesta.sh.api.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.*
import org.agrfesta.sh.api.domain.failures.ActuatorAssignmentFailure
import org.agrfesta.sh.api.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.domain.failures.NotASensor
import org.agrfesta.sh.api.domain.failures.NotAnActuator
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.failures.SameAreaAssignment
import org.agrfesta.sh.api.domain.failures.SensorAlreadyAssigned
import org.agrfesta.sh.api.domain.failures.SensorAssignmentFailure
import org.agrfesta.sh.api.persistence.ActuatorsAssignmentsDao
import org.agrfesta.sh.api.persistence.AreaNotFoundException
import org.agrfesta.sh.api.persistence.DeviceNotFoundException
import org.agrfesta.sh.api.persistence.NotASensorException
import org.agrfesta.sh.api.persistence.NotAnActuatorException
import org.agrfesta.sh.api.persistence.SameAreaAssignmentException
import org.agrfesta.sh.api.persistence.SensorAlreadyAssignedException
import org.agrfesta.sh.api.persistence.SensorsAssignmentsDao
import org.springframework.stereotype.Service

@Service
class AssignmentsService(
    private val sensorsAssignmentsDao: SensorsAssignmentsDao,
    private val actuatorsAssignmentsDao: ActuatorsAssignmentsDao
) {

    fun assignSensorToArea(areaId: UUID, deviceId: UUID): Either<SensorAssignmentFailure, Unit> {
        return try {
            sensorsAssignmentsDao.assign(areaId, deviceId).right()
        } catch (e: AreaNotFoundException) {
            AreaNotFound.left()
        } catch (e: DeviceNotFoundException) {
            DeviceNotFound.left()
        } catch (e: SameAreaAssignmentException) {
            SameAreaAssignment.left()
        } catch (e: NotASensorException) {
            NotASensor.left()
        } catch (e: SensorAlreadyAssignedException) {
            SensorAlreadyAssigned.left()
        } catch (e: Exception) {
            PersistenceFailure(e).left()
        }
    }

    fun assignActuatorToArea(areaId: UUID, deviceId: UUID): Either<ActuatorAssignmentFailure, Unit> {
        return try {
            actuatorsAssignmentsDao.assign(areaId, deviceId).right()
        } catch (e: AreaNotFoundException) {
            AreaNotFound.left()
        } catch (e: DeviceNotFoundException) {
            DeviceNotFound.left()
        } catch (e: SameAreaAssignmentException) {
            SameAreaAssignment.left()
        } catch (e: NotAnActuatorException) {
            NotAnActuator.left()
        } catch (e: Exception) {
            PersistenceFailure(e).left()
        }
    }

}
