package org.agrfesta.sh.api.core.application.ports.inbounds.heating

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.SetHeatingStrategyFailure
import org.agrfesta.sh.api.core.domain.heating.SharedHeatingStrategy

interface SetHeatingStrategyUseCase {

    /**
     * Saves the `heating.strategy` property. No heater is commanded: the next heating evaluation applies it.
     *
     * @param strategy the shared heating strategy to save.
     * @return [Either.Right] with [Unit] once the strategy is saved,
     * or [Either.Left] with [SetHeatingStrategyFailure] if the strategy cannot be saved.
     */
    fun execute(strategy: SharedHeatingStrategy): Either<SetHeatingStrategyFailure, Unit>
}
