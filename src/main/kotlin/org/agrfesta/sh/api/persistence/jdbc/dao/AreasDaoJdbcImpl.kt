package org.agrfesta.sh.api.persistence.jdbc.dao

import java.util.*
import org.agrfesta.sh.api.domain.areas.AreaDto
import org.agrfesta.sh.api.persistence.AreaDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasJdbcRepository
import org.springframework.stereotype.Service

@Service
class AreasDaoJdbcImpl(
    private val areasRepository: AreasJdbcRepository
): AreaDao {

    override fun save(area: AreaDto) = areasRepository.persist(area)

    override fun findAreaByName(name: String): AreaDto? = areasRepository.findAreaByName(name)?.asArea()

    override fun getAreaById(uuid: UUID): AreaDto = areasRepository.getAreaById(uuid).asArea()

    override fun getAreaByName(name: String): AreaDto = areasRepository.getAreaByName(name).asArea()

    override fun getAll(): Collection<AreaDto> = areasRepository.getAll().map { it.asArea() }

    override fun deleteAreaById(uuid: UUID) = areasRepository.deleteAreaById(uuid)

}
