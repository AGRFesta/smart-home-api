package org.agrfesta.sh.api.persistence.jdbc.entities

import java.time.Instant
import java.util.UUID

class SensorAssignmentEntity(
    val uuid: UUID,
    val sensorUuid: UUID,
    val areaUuid: UUID,
    val connectedOn: Instant,
    var disconnectedOn: Instant? = null
)
