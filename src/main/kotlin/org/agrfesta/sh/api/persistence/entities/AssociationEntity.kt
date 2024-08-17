package org.agrfesta.sh.api.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.*

@Entity
@Table(name = "association", schema = "smart_home")
class AssociationEntity(
    @Id val uuid: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_uuid")
    val room: RoomEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_uuid")
    val device: DeviceEntity,

    @Column(name = "connected_on")
    val connectedOn: Instant,

    @Column(name = "disconnected_on")
    val disconnectedOn: Instant? = null
)
