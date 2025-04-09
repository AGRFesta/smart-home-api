package org.agrfesta.sh.api.persistence

import java.util.*
import org.agrfesta.sh.api.domain.Area

interface AreaDao {

    /**
     * @throws SameNameAreaException when an [Area] with that name already exist.
     */
    fun save(area: Area)

    fun findAreaByName(name: String): Area?

    /**
     * @throws AreaNotFoundException when an [Area] with id [uuid] is missing.
     */
    fun getAreaById(uuid: UUID): Area

    /**
     * @throws AreaNotFoundException when an [Area] with name [name] is missing.
     */
    fun getAreaByName(name: String): Area

    fun getAll(): Collection<Area>

    fun deleteAreaById(uuid: UUID)
}

class AreaNotFoundException: Exception()
class SameNameAreaException: Exception()
