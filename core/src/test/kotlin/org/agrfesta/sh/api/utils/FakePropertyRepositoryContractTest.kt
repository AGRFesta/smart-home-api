package org.agrfesta.sh.api.utils

import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class FakePropertyRepositoryContractTest : PropertyRepositoryContract() {
    private val clock = SettableTimeProvider()

    override val sut = FakePropertyRepository(clock)

    override fun givenNow(instant: Instant) {
        clock.current = instant
    }

    /**
     * A [TimeProvider] that returns the instant last set on [current].
     */
    private class SettableTimeProvider : TimeProvider {
        var current: Instant = Instant.now()

        override fun now(): Instant = current
        override fun currentLocalTime(): LocalTime = LocalTime.ofInstant(current, ZoneId.systemDefault())
    }
}
