package org.agrfesta.sh.api.persistence

import arrow.core.Either
import org.agrfesta.sh.api.domain.Room
import java.util.*

interface RoomsDao {
    fun save(room: Room): Either<RoomCreationFailure, Room>
    fun findRoomByName(name: String): Either<PersistenceFailure, Room?>
    fun getRoomById(uuid: UUID): Either<GetRoomFailure, Room>
    fun getRoomByName(name: String): Either<GetRoomFailure, Room>
    fun getAll(): Either<PersistenceFailure, Collection<Room>>
}

sealed interface GetRoomFailure: AssociationFailure
sealed interface RoomCreationFailure

data object RoomNotFound: GetRoomFailure
data object RoomNameConflict: RoomCreationFailure
