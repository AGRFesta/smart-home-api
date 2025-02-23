package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import arrow.core.left
import java.util.*
import org.agrfesta.sh.api.domain.Area
import org.agrfesta.sh.api.domain.failures.AssociationConflict
import org.agrfesta.sh.api.domain.failures.AssociationFailure
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.failures.SameAreaAssociation
import org.agrfesta.sh.api.persistence.AssociationSuccess
import org.agrfesta.sh.api.persistence.AssociationsDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.AssociationsJdbcRepository
import org.springframework.stereotype.Service

@Service
class AssociationsDaoJdbcImpl(
    private val associationsJdbcRepository: AssociationsJdbcRepository
): AssociationsDao {

    override fun associate(areaId: UUID, deviceId: UUID): Either<AssociationFailure, AssociationSuccess> {
        val activeAssociations = associationsJdbcRepository.findByDevice(deviceId).fold(
            { return@associate it.left() },
            { it }
        ).filter { it.disconnectedOn == null }
        if (activeAssociations.isNotEmpty()) {
            val sameArea: Boolean = activeAssociations
                .map { it.areaUuid }.contains(areaId)
            return if (sameArea) SameAreaAssociation.left() else AssociationConflict.left()
        }
        return associationsJdbcRepository.persistAssociation(areaId, deviceId)
    }

    override fun deviceAssociatedArea(deviceId: UUID): Either<PersistenceFailure, Area?> {
        TODO("Not yet implemented")
    }
}
