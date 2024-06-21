package org.agrfesta.sh.api.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.agrfesta.sh.api.domain.Device
import org.agrfesta.sh.api.domain.DeviceStatus
import org.agrfesta.sh.api.domain.Provider
import java.time.Instant
import java.util.*

@Entity
@Table(name="device", schema = "smart_home")
class DeviceEntity(
    @Id
    val uuid: UUID = UUID.randomUUID(),

    var name: String,

    @Enumerated(EnumType.STRING)
    val provider: Provider,

    @Enumerated(EnumType.STRING)
    var status: DeviceStatus,

    @Column(name = "provider_id")
    val providerId: String,

    @Column(name = "created_on")
    val createdOn: Instant,

    @Column(name = "updated_on")
    var updatedOn: Instant? = null
) {
    fun toDevice() = Device(
        name = name,
        provider = provider,
        providerId = providerId,
        status = status
    )
}
