package org.agrfesta.sh.api.persistence.jdbc.entities

import org.agrfesta.sh.api.domain.Room
import java.time.Instant
import java.util.*

class RoomEntity(
    val uuid: UUID,
    var name: String,
    val createdOn: Instant,
    var updatedOn: Instant? = null
) {
    fun asRoom() = Room(uuid, name)
}