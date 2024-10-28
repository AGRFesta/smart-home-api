package org.agrfesta.sh.api.persistence

import arrow.core.Either
import org.agrfesta.sh.api.domain.Area
import java.util.*

interface AssociationsDao {
    fun associate(areaId: UUID, deviceId: UUID): Either<AssociationFailure, AssociationSuccess>
    fun deviceAssociatedArea(deviceId: UUID): Either<PersistenceFailure, Area?>
}

object AssociationSuccess

sealed interface AssociationFailure

data object AssociationConflict: AssociationFailure
data object SameAreaAssociation: AssociationFailure

