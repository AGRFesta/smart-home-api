package org.agrfesta.sh.api.core.application.usecases.notifications

import org.agrfesta.sh.api.core.application.ports.inbounds.notifications.PruneNotificationsUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.notifications.NotificationsRepository
import org.agrfesta.sh.api.core.domain.notifications.RetentionPolicy
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.stereotype.Service

@Service
class PruneNotificationsService(
    private val notificationsRepository: NotificationsRepository,
    private val retentionPolicy: RetentionPolicy,
    private val timeProvider: TimeProvider
) : PruneNotificationsUseCase {

    private val logger by LoggerDelegate()

    override fun execute() {
        val threshold = timeProvider.now().minus(retentionPolicy.window)
        notificationsRepository.deleteOlderThan(threshold)
            .onRight { deleted -> logger.info("Pruned $deleted notifications older than $threshold") }
            .onLeft { failure -> logger.warn("Failed to prune notifications older than $threshold: $failure") }
    }
}
