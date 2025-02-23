package org.agrfesta.sh.api.persistence

import arrow.core.Either
import org.agrfesta.sh.api.domain.Area
import java.util.*
import org.agrfesta.sh.api.domain.failures.AssociationFailure
import org.agrfesta.sh.api.domain.failures.PersistenceFailure

interface AssociationsDao {
    fun associate(areaId: UUID, deviceId: UUID): Either<AssociationFailure, AssociationSuccess>
    fun deviceAssociatedArea(deviceId: UUID): Either<PersistenceFailure, Area?>
}

object AssociationSuccess
