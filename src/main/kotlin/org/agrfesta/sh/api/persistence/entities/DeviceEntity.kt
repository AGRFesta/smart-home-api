package org.agrfesta.sh.api.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.agrfesta.sh.api.domain.Device
import org.agrfesta.sh.api.domain.Provider
import java.time.Instant
import java.util.*

@Entity
@Table(name="device", schema = "smart_home")
class DeviceEntity(
    @Id
    val uuid: UUID,

    var name: String,

    val provider: Provider,

    @Column(name = "provider_id")
    val providerId: String,

    @Column(name = "created_on")
    val createdOn: Instant,

    @Column(name = "updated_on")
    var updatedOn: Instant? = null
) {
    fun toDevice() = Device(
        uuid = uuid,
        name = name,
        provider = provider,
        providerId = providerId
    )
}
