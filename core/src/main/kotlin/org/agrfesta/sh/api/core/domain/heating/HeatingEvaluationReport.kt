package org.agrfesta.sh.api.core.domain.heating

import org.agrfesta.sh.api.core.domain.failures.ActuatorOperationFailure
import java.util.UUID

/**
 * Outcome of a heating evaluation run.
 */
sealed interface HeatingEvaluationReport {

    /** The evaluation did not run because heating is disabled. */
    data object Skipped : HeatingEvaluationReport

    /** The evaluation ran; one outcome per heater driven. */
    data class Evaluated(val heaters: List<HeaterActionOutcome>) : HeatingEvaluationReport
}

/** Per-heater outcome of a heating evaluation run. */
data class HeaterActionOutcome(
    val heaterId: UUID,
    val command: HeaterCommand,
    val outcome: ActuationOutcome
)

/** How the command decided for a heater turned out. */
sealed interface ActuationOutcome {

    /** The command was issued to the heater successfully. */
    data object Issued : ActuationOutcome

    /** The decision required no command. */
    data object NotNeeded : ActuationOutcome

    /** The command was issued but the actuation failed; [cause] carries the device failure. */
    data class Failed(val cause: ActuatorOperationFailure) : ActuationOutcome
}
