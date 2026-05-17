package org.agrfesta.sh.api.schedulers

import org.agrfesta.sh.api.core.application.ports.inbounds.EvaluateHeatingStateUseCase
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class HeatingControlScheduler(
    private val evaluateHeatingState: EvaluateHeatingStateUseCase
) {
    @Scheduled(cron = "0 */15 * * * *")
    @Async
    fun scheduledTask() {
        evaluateHeatingState.execute()
    }
}
