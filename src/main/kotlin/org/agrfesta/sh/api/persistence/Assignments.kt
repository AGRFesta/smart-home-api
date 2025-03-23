package org.agrfesta.sh.api.persistence

import java.util.*

interface ActuatorsAssignmentsDao {

    /**
     *
     *
     * @throws AreaNotFoundException when area with id [areaId] is missing.
     * @throws DeviceNotFoundException when device with id [actuatorId] is missing.
     * @throws NotAnActuatorException when device with id [actuatorId] is not an actuator.
     * @throws SameAreaAssignmentException when actuator is already assigned to this area.
     */
    fun assign(areaId: UUID, actuatorId: UUID)
}

interface SensorsAssignmentsDao {

    /**
     *
     *
     * @throws AreaNotFoundException when area with id [areaId] is missing.
     * @throws DeviceNotFoundException when device with id [sensorId] is missing.
     * @throws NotASensorException when device with id [sensorId] is not a sensor.
     * @throws SensorAlreadyAssignedException when sensor with id [sensorId] is already assigned to another area.
     */
    fun assign(areaId: UUID, sensorId: UUID)
}

class SameAreaAssignmentException: Exception()
class SensorAlreadyAssignedException: Exception()
