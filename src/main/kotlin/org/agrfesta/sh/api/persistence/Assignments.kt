package org.agrfesta.sh.api.persistence

import java.util.*

interface ActuatorsAssignmentsDao {

    /**
     * Assigns actuator with id [actuatorId] to area with id [areaId].
     *
     * @param areaId Area's unique identifier.
     * @param actuatorId Actuator's unique identifier.
     * @throws AreaNotFoundException when area with id [areaId] is missing.
     * @throws DeviceNotFoundException when device with id [actuatorId] is missing.
     * @throws NotAnActuatorException when device with id [actuatorId] is not an actuator.
     * @throws SameAreaAssignmentException when actuator is already assigned to this area.
     */
    fun assign(areaId: UUID, actuatorId: UUID)
}

interface SensorsAssignmentsDao {

    /**
     * Assigns actuator with id [sensorId] to area with id [areaId].
     *
     * @param areaId Area's unique identifier.
     * @param sensorId Sensor's unique identifier.
     * @throws AreaNotFoundException when area with id [areaId] is missing.
     * @throws DeviceNotFoundException when device with id [sensorId] is missing.
     * @throws NotASensorException when device with id [sensorId] is not a sensor.
     * @throws SensorAlreadyAssignedException when sensor with id [sensorId] is already assigned to another area.
     */
    fun assign(areaId: UUID, sensorId: UUID)

    /**
     * Removes assignment of sensor with id [sensorId], if exists.
     * Considering that a sensor can be assigned to a single area we don't need to specify the area.
     *
     * @param sensorId Sensor's unique identifier.
     * @throws DeviceNotFoundException when device with id [sensorId] is missing.
     * @throws NotASensorException when device with id [sensorId] is not a sensor.
     */
    fun unassign(sensorId: UUID)
}

class SameAreaAssignmentException: Exception()
class SensorAlreadyAssignedException: Exception()
