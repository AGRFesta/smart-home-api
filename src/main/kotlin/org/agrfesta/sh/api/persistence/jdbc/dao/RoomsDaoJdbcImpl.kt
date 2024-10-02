package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import org.agrfesta.sh.api.domain.Room
import org.agrfesta.sh.api.persistence.GetRoomFailure
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.RoomCreationFailure
import org.agrfesta.sh.api.persistence.RoomsDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.RoomsJdbcRepository
import org.springframework.stereotype.Service
import java.util.*

@Service
class RoomsDaoJdbcImpl(
    private val roomsRepository: RoomsJdbcRepository
): RoomsDao {

    override fun save(room: Room): Either<RoomCreationFailure, Room> = roomsRepository.persist(room)

    override fun findRoomByName(name: String): Either<PersistenceFailure, Room?> =
        roomsRepository.findRoomByName(name).map { it?.asRoom() }

    override fun getRoomById(uuid: UUID): Either<GetRoomFailure, Room> =
        roomsRepository.getRoomById(uuid).map { it.asRoom() }

    override fun getRoomByName(name: String): Either<GetRoomFailure, Room> =
        roomsRepository.getRoomByName(name).map { it.asRoom() }

    override fun getAll(): Either<PersistenceFailure, Collection<Room>> = roomsRepository.getAll()
        .map { it.map { entity -> entity.asRoom() } }

}
