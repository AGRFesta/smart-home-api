package org.agrfesta.sh.api.core.application.ports.inbounds.devices

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.AcSettingsUpdate
import org.agrfesta.sh.api.core.domain.failures.SetAcSettingsFailure
import java.util.UUID

interface SetAcSettingsUseCase {

    /**
     * Resolves the persisted device by [deviceId], routes to its provider's air conditioner
     * driver and applies [update] as a partial update: only the provided fields change, the
     * driver fills the rest from the device's current state (one wire command per call).
     *
     * @param deviceId the unique identifier of the device to drive.
     * @param update the settings to change; fields left null keep the device's current value.
     * @return [Either.Right] when the device accepted the command,
     * or [Either.Left] with a [SetAcSettingsFailure].
     */
    fun execute(deviceId: UUID, update: AcSettingsUpdate): Either<SetAcSettingsFailure, Unit>
}
