package org.agrfesta.sh.api.home

import org.springframework.context.ApplicationEvent

/**
 * Payload-less in-process signal meaning "the home state may have changed, push to connected clients".
 *
 * Published by [SpringHomeStateRefreshPublisher] and consumed by the SSE broadcaster.
 */
class HomeStateRefreshEvent(source: Any) : ApplicationEvent(source)
