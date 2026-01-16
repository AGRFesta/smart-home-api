package org.agrfesta.sh.api.utils

import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

interface TimeService {
    fun now(): Instant
    fun toLocalTime(instant: Instant): LocalTime
}

@Service
class TimeServiceImpl(private val clock: Clock): TimeService {
    override fun now(): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
    override fun toLocalTime(instant: Instant): LocalTime = instant.atZone(clock.zone).toLocalTime()
}
