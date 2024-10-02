package org.agrfesta.sh.api.persistence.jdbc.entities

import java.time.Instant
import java.util.UUID

class AssociationEntity(
    val uuid: UUID,
    val deviceUuid: UUID,
    val roomUuid: UUID,
    val connectedOn: Instant,
    var disconnectedOn: Instant? = null
) {

}
