package org.agrfesta.sh.api.persistence

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.domain.Room
import org.agrfesta.sh.api.persistence.entities.RoomEntity
import org.agrfesta.sh.api.persistence.repositories.RoomsRepository
import org.agrfesta.sh.api.utils.TimeService
import org.springframework.stereotype.Service

interface RoomsDao {
    fun save(room: Room): Either<RoomCreationFailure, Room>
    fun findRoomByName(name: String): Either<RoomPersistenceFailure, Room?>
    fun getRoomByName(name: String): Either<GetRoomFailure, Room>
    fun getAll(): Either<RoomPersistenceFailure, Collection<Room>>
}

sealed interface GetRoomFailure
sealed interface RoomCreationFailure

data object RoomNotFound: GetRoomFailure
data object RoomNameConflict: RoomCreationFailure

data class RoomPersistenceFailure(val exception: Exception): GetRoomFailure, RoomCreationFailure

@Service
class RoomsDaoImpl(
    private val roomsRepository: RoomsRepository,
    private val timeService: TimeService
): RoomsDao {

    override fun save(room: Room): Either<RoomCreationFailure, Room> {
        when (val roomsResult = getAll()) {
            is Right -> {
                val names = roomsResult.value.map { it.name }.toSet()
                if (names.contains(room.name)) return RoomNameConflict.left()
            }
            is Left -> return roomsResult
        }
        return try {
            roomsRepository.save(
                RoomEntity(
                    uuid = room.uuid,
                    name = room.name,
                    createdOn = timeService.now() ))
                .toRoom()
                .right()
        } catch (e: Exception) {
            RoomPersistenceFailure(e).left()
        }
    }

    override fun findRoomByName(name: String): Either<RoomPersistenceFailure, Room?> = try {
        roomsRepository.findByName(name)
            ?.toRoom()
            .right()
    } catch (e: Exception) {
        RoomPersistenceFailure(e).left()
    }

    override fun getRoomByName(name: String): Either<GetRoomFailure, Room> = try {
        roomsRepository.findByName(name)
            ?.toRoom()
            ?.right()
            ?: RoomNotFound.left()
    } catch (e: Exception) {
        RoomPersistenceFailure(e).left()
    }

    override fun getAll(): Either<RoomPersistenceFailure, Collection<Room>> = try {
        roomsRepository.findAll()
            .map { it.toRoom() }
            .right()
    } catch (e: Exception) {
        RoomPersistenceFailure(e).left()
    }

}
