package org.agrfesta.sh.api.configuration

import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Service
class TimeProviderImpl(private val clock: Clock) : TimeProvider {
    override fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
    override fun currentLocalTime(): LocalTime = now().atZone(clock.zone).toLocalTime()
}
