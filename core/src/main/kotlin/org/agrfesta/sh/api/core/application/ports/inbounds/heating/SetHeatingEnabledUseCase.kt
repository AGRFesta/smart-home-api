package org.agrfesta.sh.api.core.application.ports.inbounds.heating

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.SetHeatingEnabledFailure
import org.agrfesta.sh.api.core.domain.heating.SetHeatingEnabledReport

interface SetHeatingEnabledUseCase {

    /**
     * Saves the `heating.enabled` flag. Enabling commands no heater: the next heating evaluation drives them.
     * Disabling then sends OFF once to every registered heater, after any heating evaluation in progress;
     * a failed OFF does not stop the others and is not retried. See `docs/api/heating.md`.
     *
     * @param enabled whether heating is enabled.
     * @return [Either.Right] with [SetHeatingEnabledReport] once the flag is saved and every OFF is sent,
     * or [Either.Left] with [SetHeatingEnabledFailure] if the flag cannot be saved, or if it is saved but the
     * registered heaters cannot be read.
     */
    fun execute(enabled: Boolean): Either<SetHeatingEnabledFailure, SetHeatingEnabledReport>
}
