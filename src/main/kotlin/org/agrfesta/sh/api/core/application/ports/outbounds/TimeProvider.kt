package org.agrfesta.sh.api.core.application.ports.outbounds

import java.time.Instant
import java.time.LocalTime

interface TimeProvider {
    fun now(): Instant
    fun currentLocalTime(): LocalTime
}
