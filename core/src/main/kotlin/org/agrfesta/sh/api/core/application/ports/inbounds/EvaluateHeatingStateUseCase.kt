package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.EvaluateHeatingStateFailure
import org.agrfesta.sh.api.core.domain.heating.HeatingEvaluationReport

interface EvaluateHeatingStateUseCase {

    /**
     * Evaluates the current heating state across all heatable areas and drives actuators accordingly.
     *
     * Reads the `heating.enabled` flag from the property store; if disabled or missing, returns
     * [HeatingEvaluationReport.Skipped] without touching any device. Otherwise fetches devices and
     * areas, groups heatable areas by shared heater, delegates the decision to the configured heating
     * strategy and returns [HeatingEvaluationReport.Evaluated] with one outcome per heater: the command
     * decided and whether it was issued, failed, or not needed. A failed actuation is a partial outcome
     * in the report, not a failure of the whole evaluation.
     *
     * Returns an [EvaluateHeatingStateFailure] when the flag, the devices or the areas cannot be
     * fetched; in that case no actuator is driven.
     */
    fun execute(): Either<EvaluateHeatingStateFailure, HeatingEvaluationReport>
}
