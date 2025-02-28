package org.agrfesta.sh.api.persistence.jdbc.entities

import java.time.Instant
import java.util.UUID

class AssignmentEntity(
    val uuid: UUID,
    val deviceUuid: UUID,
    val areaUuid: UUID,
    val connectedOn: Instant,
    var disconnectedOn: Instant? = null
)
