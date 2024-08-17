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
import java.util.*
import kotlin.jvm.optionals.getOrNull

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
            PersistenceFailure(e).left()
        }
    }

    override fun findRoomByName(name: String): Either<PersistenceFailure, Room?> = try {
        roomsRepository.findByName(name)
            ?.toRoom()
            .right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    override fun getRoomById(uuid: UUID): Either<GetRoomFailure, Room> = try {
        roomsRepository.findById(uuid)
            .map { it.toRoom() }
            .getOrNull()
            ?.right()
            ?: RoomNotFound.left()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    override fun getRoomByName(name: String): Either<GetRoomFailure, Room> = try {
        roomsRepository.findByName(name)
            ?.toRoom()
            ?.right()
            ?: RoomNotFound.left()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    override fun getAll(): Either<PersistenceFailure, Collection<Room>> = try {
        roomsRepository.findAll()
            .map { it.toRoom() }
            .right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

}
