package org.agrfesta.sh.api.home

import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Outbound adapter publishing the home-state refresh signal as an in-process Spring [HomeStateRefreshEvent].
 */
@Component
class SpringHomeStateRefreshPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) : HomeStateRefreshPublisher {

    override fun publish() {
        applicationEventPublisher.publishEvent(HomeStateRefreshEvent(this))
    }
}
