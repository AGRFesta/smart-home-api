package org.agrfesta.sh.api.persistence.jdbc.entities

import org.agrfesta.sh.api.domain.areas.AreaDto
import java.time.Instant
import java.util.*

class AreaEntity(
    val uuid: UUID,
    var name: String,
    val isIndoor: Boolean,
    val createdOn: Instant,
    var updatedOn: Instant? = null
) {
    fun asArea() = AreaDto(uuid, name, isIndoor = isIndoor)
}