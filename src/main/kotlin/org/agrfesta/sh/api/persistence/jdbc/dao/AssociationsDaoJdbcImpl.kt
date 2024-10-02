package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.domain.Room
import org.agrfesta.sh.api.persistence.AssociationConflict
import org.agrfesta.sh.api.persistence.AssociationFailure
import org.agrfesta.sh.api.persistence.AssociationSuccess
import org.agrfesta.sh.api.persistence.AssociationsDao
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.SameRoomAssociation
import org.agrfesta.sh.api.persistence.jdbc.repositories.AssociationsJdbcRepository
import org.springframework.stereotype.Service
import java.util.*

@Service
class AssociationsDaoJdbcImpl(
    private val associationsJdbcRepository: AssociationsJdbcRepository
): AssociationsDao {

    override fun associate(roomId: UUID, deviceId: UUID): Either<AssociationFailure, AssociationSuccess> {
        val activeAssociations = associationsJdbcRepository.findByDevice(deviceId).fold(
            { return@associate it.left() },
            { it }
        ).filter { it.disconnectedOn == null }
        if (activeAssociations.isNotEmpty()) {
            val sameRoom: Boolean = activeAssociations
                .map { it.roomUuid }.contains(roomId)
            return if (sameRoom) SameRoomAssociation.left() else AssociationConflict.left()
        }
        return associationsJdbcRepository.persistAssociation(roomId, deviceId)
    }

    override fun deviceAssociatedRoom(deviceId: UUID): Either<PersistenceFailure, Room?> {
        TODO("Not yet implemented")
    }
}
