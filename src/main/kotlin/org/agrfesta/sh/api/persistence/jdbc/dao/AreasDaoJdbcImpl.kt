package org.agrfesta.sh.api.persistence.jdbc.dao

import java.util.*
import org.agrfesta.sh.api.domain.Area
import org.agrfesta.sh.api.persistence.AreaDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasJdbcRepository
import org.springframework.stereotype.Service

@Service
class AreasDaoJdbcImpl(
    private val areasRepository: AreasJdbcRepository
): AreaDao {

    override fun save(area: Area) = areasRepository.persist(area)

    override fun findAreaByName(name: String): Area? = areasRepository.findAreaByName(name)?.asArea()

    override fun getAreaById(uuid: UUID): Area = areasRepository.getAreaById(uuid).asArea()

    override fun getAreaByName(name: String): Area = areasRepository.getAreaByName(name).asArea()

    override fun getAll(): Collection<Area> = areasRepository.getAll().map { it.asArea() }

}
