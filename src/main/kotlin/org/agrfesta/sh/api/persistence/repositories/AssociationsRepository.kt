package org.agrfesta.sh.api.persistence.repositories

import org.agrfesta.sh.api.persistence.entities.DeviceEntity
import org.agrfesta.sh.api.persistence.entities.AssociationEntity
import org.springframework.data.repository.CrudRepository
import java.util.*

interface AssociationsRepository: CrudRepository<AssociationEntity, UUID> {
    fun findByDevice(device: DeviceEntity): Collection<AssociationEntity>
}
