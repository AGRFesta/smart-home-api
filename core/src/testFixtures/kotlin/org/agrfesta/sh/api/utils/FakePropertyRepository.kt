package org.agrfesta.sh.api.utils

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.commons.PropertyUpsertEntry
import org.agrfesta.sh.api.core.domain.failures.FindPropertyFailure
import org.agrfesta.sh.api.core.domain.failures.GetPropertyFailure
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * In-memory [PropertyRepository] for state-based tests (Chicago style): arrange with
 * [givenProperty], assert on [storedValue] — no interaction verification needed.
 * An entry expires `ttl` seconds after the current instant of [timeProvider], truncated to the second, and
 * [findEntry] and [getEntry] skip an expired entry. [PropertyRepositoryContract] holds it to the outcomes of the
 * JDBC adapter.
 * [givenUpsertFails] makes every later [upsert] and [upsertBatch] return [PropertyRepositoryError] and store nothing.
 * [givenReadFails] makes every later [findEntry] and [getEntry] return [PropertyRepositoryError].
 */
class FakePropertyRepository(
    private val timeProvider: TimeProvider = SystemTimeProvider
) : PropertyRepository {
    private val store = mutableMapOf<String, PropertyEntry>()
    private var upsertFails = false
    private var readFails = false

    /**
     * Stores [value] under [key] with no expiry.
     */
    fun givenProperty(key: String, value: String) {
        store[key] = PropertyEntry(value)
    }

    fun givenUpsertFails() {
        upsertFails = true
    }

    fun givenReadFails() {
        readFails = true
    }

    /**
     * The value stored under [key], expired or not.
     */
    fun storedValue(key: String): String? = store[key]?.value

    override fun upsert(key: String, value: String, ttl: Long?): Either<PropertyRepositoryError, Unit> =
        upsertBatch(listOf(PropertyUpsertEntry(key, value, ttl)))

    override fun upsertBatch(entries: List<PropertyUpsertEntry>): Either<PropertyRepositoryError, Unit> {
        if (upsertFails) return PropertyRepositoryError.left()
        val now = timeProvider.now().truncatedTo(ChronoUnit.SECONDS)
        entries.forEach { store[it.key] = PropertyEntry(it.value, it.ttl?.let(now::plusSeconds)) }
        return Unit.right()
    }

    override fun findEntry(key: String): Either<FindPropertyFailure, PropertyEntry?> =
        if (readFails) PropertyRepositoryError.left() else liveEntry(key).right()

    override fun getEntry(key: String): Either<GetPropertyFailure, PropertyEntry> = when {
        readFails -> PropertyRepositoryError.left()
        else -> liveEntry(key)?.right() ?: PropertyNotFound.left()
    }

    private fun liveEntry(key: String): PropertyEntry? {
        val now = timeProvider.now()
        return store[key]?.takeIf { entry -> entry.expiresAt?.isAfter(now) ?: true }
    }
}

/**
 * The [TimeProvider] of a [FakePropertyRepository] built without one: it reads the system clock.
 */
private object SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun currentLocalTime(): LocalTime = LocalTime.now()
}
