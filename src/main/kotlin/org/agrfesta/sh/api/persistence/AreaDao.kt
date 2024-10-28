package org.agrfesta.sh.api.persistence

import arrow.core.Either
import org.agrfesta.sh.api.domain.Area
import java.util.*

interface AreaDao {
    fun save(area: Area): Either<AreaCreationFailure, Area>
    fun findAreaByName(name: String): Either<PersistenceFailure, Area?>
    fun getAreaById(uuid: UUID): Either<GetAreaFailure, Area>
    fun getAreaByName(name: String): Either<GetAreaFailure, Area>
    fun getAll(): Either<PersistenceFailure, Collection<Area>>
}

sealed interface GetAreaFailure: AssociationFailure
sealed interface AreaCreationFailure

data object AreaNotFound: GetAreaFailure
data object AreaNameConflict: AreaCreationFailure
