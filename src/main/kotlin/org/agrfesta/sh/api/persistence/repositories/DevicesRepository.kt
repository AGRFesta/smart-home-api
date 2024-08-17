package org.agrfesta.sh.api.persistence.repositories

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.domain.devices.Provider
import org.agrfesta.sh.api.persistence.DeviceNotFound
import org.agrfesta.sh.api.persistence.GetDeviceFailure
import org.agrfesta.sh.api.persistence.PersistenceFailure
import org.agrfesta.sh.api.persistence.entities.DeviceEntity
import org.springframework.data.repository.CrudRepository
import java.util.*
import kotlin.jvm.optionals.getOrNull

interface DevicesRepository: CrudRepository<DeviceEntity, UUID> {
    fun findByProviderAndProviderId(provider: Provider, providerId: String): DeviceEntity?
}

fun DevicesRepository.findDeviceByUuid(uuid: UUID): Either<GetDeviceFailure, DeviceEntity> = try {
        findById(uuid)
            .getOrNull()
            ?.right()
            ?: DeviceNotFound.left()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }
