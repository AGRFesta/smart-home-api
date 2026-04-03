package org.agrfesta.sh.api.schedulers

import org.agrfesta.sh.api.services.heating.HeatingOrchestrationService
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class HeatingControlScheduler(
    private val heatingOrchestrationService: HeatingOrchestrationService
) {
    @Scheduled(cron = "0 */15 * * * *")
    @Async
    fun scheduledTask() {
        heatingOrchestrationService.evaluateHeatingState()
    }

}
