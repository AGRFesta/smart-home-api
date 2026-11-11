package org.agrfesta.sh.api.core.application.ports.inbounds.heating

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.SetHeatingEnabledFailure

interface SetHeatingEnabledUseCase {

    /**
     * Saves the `heating.enabled` flag. No heater is commanded: the next heating evaluation drives them.
     *
     * @param enabled whether heating is enabled.
     * @return [Either.Right] with [Unit] once the flag is saved,
     * or [Either.Left] with [SetHeatingEnabledFailure] if the flag cannot be saved.
     */
    fun execute(enabled: Boolean): Either<SetHeatingEnabledFailure, Unit>
}
