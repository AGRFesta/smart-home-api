package org.agrfesta.sh.api.persistence.repositories

import org.agrfesta.sh.api.domain.Provider
import org.agrfesta.sh.api.persistence.entities.DeviceEntity
import org.springframework.data.repository.CrudRepository
import java.util.*

interface DevicesRepository: CrudRepository<DeviceEntity, UUID> {
    fun findByProviderAndProviderId(provider: Provider, providerId: String): DeviceEntity?
}
