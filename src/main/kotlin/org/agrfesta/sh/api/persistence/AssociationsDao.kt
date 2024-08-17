package org.agrfesta.sh.api.persistence

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.domain.Room
import org.agrfesta.sh.api.persistence.entities.AssociationEntity
import org.agrfesta.sh.api.persistence.repositories.DevicesRepository
import org.agrfesta.sh.api.persistence.repositories.AssociationsRepository
import org.agrfesta.sh.api.persistence.repositories.RoomsRepository
import org.agrfesta.sh.api.persistence.repositories.findDeviceByUuid
import org.agrfesta.sh.api.persistence.repositories.findRoomById
import org.agrfesta.sh.api.utils.TimeService
import org.springframework.stereotype.Service
import java.util.*

interface AssociationsDao {
    fun associate(roomId: UUID, deviceId: UUID): Either<AssociationFailure, AssociationSuccess>
    fun deviceAssociatedRoom(deviceId: UUID): Either<PersistenceFailure, Room?>
}

object AssociationSuccess

sealed interface AssociationFailure

data object AssociationConflict: AssociationFailure
data object SameRoomAssociation: AssociationFailure

@Service
class AssociationsDaoImpl(
    private val roomsRepository: RoomsRepository,
    private val devicesRepository: DevicesRepository,
    private val associationsRepository: AssociationsRepository,
    private val timeService: TimeService
): AssociationsDao {

    override fun associate(roomId: UUID, deviceId: UUID): Either<AssociationFailure, AssociationSuccess> {
        val room = when (val e = roomsRepository.findRoomById(roomId)) {
            is Right -> e.value
            is Left -> return e.mapLeft { it }
        }
        val device = when (val e = devicesRepository.findDeviceByUuid(deviceId)) {
            is Right -> e.value
            is Left -> return e.mapLeft { it }
        }
        val activeAssociations = associationsRepository.findByDevice(device)
            .filter { it.disconnectedOn == null }
        if (activeAssociations.isNotEmpty()) {
            val sameRoom: Boolean = activeAssociations
                .map { it.room.uuid }.contains(roomId)
            if (sameRoom) return SameRoomAssociation.left()
            return AssociationConflict.left()
        }
        associationsRepository.save(
            AssociationEntity(
                room = room,
                device = device,
                connectedOn = timeService.now()
            ))
        return AssociationSuccess.right()
    }

    override fun deviceAssociatedRoom(deviceId: UUID): Either<PersistenceFailure, Room?> {
        TODO("Not yet implemented")
    }

}
