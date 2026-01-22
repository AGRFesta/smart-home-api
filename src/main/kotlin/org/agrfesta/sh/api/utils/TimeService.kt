package org.agrfesta.sh.api.utils

import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import org.springframework.stereotype.Service

/**
 * Service for handling time-related operations.
 */
interface TimeService {
    /**
     * Returns the current instant.
     *
     * @return The current [Instant].
     */
    fun now(): Instant

    /**
     * Returns the current local time based on the configured time zone.
     *
     * @return The current [LocalTime].
     */
    fun currentLocalTime(): LocalTime
}

/**
 * Implementation of [TimeService] that uses a [Clock] to retrieve the current time.
 */
@Service
class TimeServiceImpl(private val clock: Clock): TimeService {
    override fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
    override fun currentLocalTime(): LocalTime = now().atZone(clock.zone).toLocalTime()
}
