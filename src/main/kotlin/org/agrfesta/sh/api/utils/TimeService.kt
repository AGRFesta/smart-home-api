package org.agrfesta.sh.api.utils

import org.springframework.stereotype.Service
import java.time.Instant

interface TimeService {
    fun now(): Instant
}

@Service
class TimeServiceImpl: TimeService {
    override fun now(): Instant = Instant.now()
}
