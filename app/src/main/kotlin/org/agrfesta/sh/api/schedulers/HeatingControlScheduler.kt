package org.agrfesta.sh.api.schedulers

import org.agrfesta.sh.api.core.application.ports.inbounds.EvaluateHeatingStateUseCase
import org.agrfesta.sh.api.core.domain.heating.ActuationOutcome
import org.agrfesta.sh.api.core.domain.heating.HeatingEvaluationReport
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class HeatingControlScheduler(
    private val evaluateHeatingState: EvaluateHeatingStateUseCase
) {
    private val logger by LoggerDelegate()

    @Scheduled(cron = "0 */15 * * * *")
    @Async
    fun scheduledTask() {
        evaluateHeatingState.execute().fold(
            ifLeft = { logger.error("Heating evaluation failed: $it") },
            ifRight = ::logFailedActuations
        )
    }

    private fun logFailedActuations(report: HeatingEvaluationReport) {
        when (report) {
            is HeatingEvaluationReport.Evaluated -> report.heaters.forEach { heater ->
                val outcome = heater.outcome
                if (outcome is ActuationOutcome.Failed) {
                    logger.error("Heater '${heater.heaterId}' failed to actuate ${heater.command}: ${outcome.cause}")
                }
            }
            HeatingEvaluationReport.Skipped -> Unit
        }
    }
}
