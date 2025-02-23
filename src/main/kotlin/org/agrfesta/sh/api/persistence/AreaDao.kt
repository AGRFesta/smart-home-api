package org.agrfesta.sh.api.persistence

import arrow.core.Either
import org.agrfesta.sh.api.domain.Area
import java.util.*
import org.agrfesta.sh.api.domain.failures.AreaCreationFailure
import org.agrfesta.sh.api.domain.failures.GetAreaFailure
import org.agrfesta.sh.api.domain.failures.PersistenceFailure

interface AreaDao {
    fun save(area: Area): Either<AreaCreationFailure, Area>
    fun findAreaByName(name: String): Either<PersistenceFailure, Area?>
    fun getAreaById(uuid: UUID): Either<GetAreaFailure, Area>
    fun getAreaByName(name: String): Either<GetAreaFailure, Area>
    fun getAll(): Either<PersistenceFailure, Collection<Area>>
}


