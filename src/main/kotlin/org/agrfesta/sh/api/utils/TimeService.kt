package org.agrfesta.sh.api.utils

import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

interface TimeService {
    fun now(): Instant
}

fun generateNoNanosInstant() = Instant.now().truncatedTo(ChronoUnit.MILLIS)

@Service
class TimeServiceImpl: TimeService {
    override fun now(): Instant = generateNoNanosInstant()
}

fun Instant.toHomeDataTime(): LocalTime = atZone(ZoneId.of("Europe/Rome")).toLocalTime()
