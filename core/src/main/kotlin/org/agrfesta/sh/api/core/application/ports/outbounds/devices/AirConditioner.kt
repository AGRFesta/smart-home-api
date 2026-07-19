package org.agrfesta.sh.api.core.application.ports.outbounds.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.AcSettingsUpdate
import org.agrfesta.sh.api.core.domain.devices.AcState
import org.agrfesta.sh.api.core.domain.failures.ActuatorOperationFailure

/**
 * An air conditioner: an on/off actuator that can additionally be driven on operating mode,
 * target temperature and fan speed.
 *
 * [updateSettings] is a complete, self-contained command applying every provided field at
 * once: the driver is responsible for composing it with the device's current state when the
 * underlying protocol transmits full-state writes (as hOn's `settings` command does) — one
 * partial update, one wire command, no read-modify-write races between fields.
 */
interface AirConditioner : OnOffActuator {
    fun getState(): Either<ActuatorOperationFailure, AcState>
    fun updateSettings(update: AcSettingsUpdate): Either<ActuatorOperationFailure, Unit>
}
