package org.agrfesta.sh.api.persistence.jdbc.entities

import org.agrfesta.sh.api.core.application.readmodels.areas.AreaView
import java.time.Instant
import java.util.*

class AreaEntity(
    val uuid: UUID,
    var name: String,
    val isIndoor: Boolean,
    val createdOn: Instant,
    var updatedOn: Instant? = null
) {
    fun asArea() = AreaView(uuid, name, isIndoor = isIndoor)
}
