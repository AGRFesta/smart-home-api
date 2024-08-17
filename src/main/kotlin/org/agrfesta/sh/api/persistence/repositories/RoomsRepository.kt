package org.agrfesta.sh.api.persistence.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.persistence.GetRoomFailure
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.RoomNotFound
import org.agrfesta.sh.api.persistence.entities.RoomEntity
import org.springframework.data.repository.CrudRepository
import java.util.*
import kotlin.jvm.optionals.getOrNull

interface RoomsRepository: CrudRepository<RoomEntity, UUID> {
    fun findByName(name: String): RoomEntity?
}

fun RoomsRepository.findRoomById(uuid: UUID): Either<GetRoomFailure, RoomEntity> = try {
        findById(uuid)
            .getOrNull()
            ?.right()
            ?: RoomNotFound.left()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }
