package org.agrfesta.sh.api.persistence.repositories

import org.agrfesta.sh.api.persistence.entities.RoomEntity
import org.springframework.data.repository.CrudRepository
import java.util.*

interface RoomsRepository: CrudRepository<RoomEntity, UUID> {
    fun findByName(name: String): RoomEntity?
}
