package org.agrfesta.sh.api.persistence.jdbc.entities

import java.util.*

class ActuatorAssignmentEntity (
    val uuid: UUID,
    val actuatorUuid: UUID,
    val areaUuid: UUID
)
