package org.agrfesta.sh.api.persistence

import java.util.*
import org.agrfesta.sh.api.domain.areas.AreaDto

interface AreaDao {

    /**
     * @throws SameNameAreaException when an [AreaDto] with that name already exist.
     */
    fun save(area: AreaDto)

    fun findAreaByName(name: String): AreaDto?

    /**
     * @throws AreaNotFoundException when an [AreaDto] with id [uuid] is missing.
     */
    fun getAreaById(uuid: UUID): AreaDto

    /**
     * @throws AreaNotFoundException when an [AreaDto] with name [name] is missing.
     */
    fun getAreaByName(name: String): AreaDto

    fun getAll(): Collection<AreaDto>

    fun deleteAreaById(uuid: UUID)
}

class AreaNotFoundException: Exception()
class SameNameAreaException: Exception()
