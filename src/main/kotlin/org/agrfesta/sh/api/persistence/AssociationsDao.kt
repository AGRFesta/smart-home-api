package org.agrfesta.sh.api.persistence

import arrow.core.Either
import org.agrfesta.sh.api.domain.Room
import java.util.*

interface AssociationsDao {
    fun associate(roomId: UUID, deviceId: UUID): Either<AssociationFailure, AssociationSuccess>
    fun deviceAssociatedRoom(deviceId: UUID): Either<PersistenceFailure, Room?>
}

object AssociationSuccess

sealed interface AssociationFailure

data object AssociationConflict: AssociationFailure
data object SameRoomAssociation: AssociationFailure

