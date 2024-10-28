package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import org.agrfesta.sh.api.domain.Area
import org.agrfesta.sh.api.persistence.GetAreaFailure
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.AreaCreationFailure
import org.agrfesta.sh.api.persistence.AreaDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasJdbcRepository
import org.springframework.stereotype.Service
import java.util.*

@Service
class AreasDaoJdbcImpl(
    private val areasRepository: AreasJdbcRepository
): AreaDao {

    override fun save(area: Area): Either<AreaCreationFailure, Area> = areasRepository.persist(area)

    override fun findAreaByName(name: String): Either<PersistenceFailure, Area?> =
        areasRepository.findAreaByName(name).map { it?.asArea() }

    override fun getAreaById(uuid: UUID): Either<GetAreaFailure, Area> =
        areasRepository.getAreaById(uuid).map { it.asArea() }

    override fun getAreaByName(name: String): Either<GetAreaFailure, Area> =
        areasRepository.getAreaByName(name).map { it.asArea() }

    override fun getAll(): Either<PersistenceFailure, Collection<Area>> = areasRepository.getAll()
        .map { it.map { entity -> entity.asArea() } }

}
