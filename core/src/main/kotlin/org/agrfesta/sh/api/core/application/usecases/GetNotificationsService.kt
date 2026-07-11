package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import org.agrfesta.sh.api.core.application.ports.inbounds.GetNotificationsUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.notifications.NotificationsRepository
import org.agrfesta.sh.api.core.application.readmodels.notifications.NotificationsPageView
import org.agrfesta.sh.api.core.domain.failures.GetNotificationsFailure
import org.springframework.stereotype.Service

@Service
class GetNotificationsService(
    private val notificationsRepository: NotificationsRepository
) : GetNotificationsUseCase {

    override fun execute(page: Int, size: Int): Either<GetNotificationsFailure, NotificationsPageView> = either {
        val items = notificationsRepository.findAll(offset = page * size, limit = size).bind()
        val total = notificationsRepository.count().bind()
        NotificationsPageView(items = items.toList(), page = page, size = size, total = total)
    }
}
