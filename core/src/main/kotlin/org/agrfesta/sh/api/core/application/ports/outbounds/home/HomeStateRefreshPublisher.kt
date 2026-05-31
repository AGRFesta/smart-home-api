package org.agrfesta.sh.api.core.application.ports.outbounds.home

/**
 * Outbound port signalling that the home state may have changed and connected dashboard clients should be refreshed.
 *
 * Implemented by an infrastructure adapter (e.g. a Spring `ApplicationEvent` publisher) so the delivery mechanism can be
 * swapped without touching the core. Callers invoke it after a successful state mutation as a fire-and-forget signal;
 * they must not depend on its outcome.
 */
interface HomeStateRefreshPublisher {

    /**
     * Signals that the home state may have changed, so that connected clients can be refreshed.
     */
    fun publish()
}
