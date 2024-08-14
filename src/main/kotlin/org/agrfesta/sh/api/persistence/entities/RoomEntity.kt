package org.agrfesta.sh.api.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.agrfesta.sh.api.domain.Room
import java.time.Instant
import java.util.*

@Entity
@Table(name="room", schema = "smart_home")
class RoomEntity(
    @Id
    val uuid: UUID,

    var name: String,

    @Column(name = "created_on")
    val createdOn: Instant,

    @Column(name = "updated_on")
    var updatedOn: Instant? = null
) {
    fun toRoom() = Room(
        uuid = uuid,
        name = name,
        devices = emptyList()
    )
}
