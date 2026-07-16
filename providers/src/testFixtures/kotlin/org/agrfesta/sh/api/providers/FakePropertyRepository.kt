package org.agrfesta.sh.api.providers

import arrow.core.Either
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.commons.PropertyUpsertEntry
import org.agrfesta.sh.api.core.domain.failures.FindPropertyFailure
import org.agrfesta.sh.api.core.domain.failures.GetPropertyFailure
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError

/**
 * In-memory [PropertyRepository] for state-based tests (Chicago style): arrange with
 * [givenProperty], assert on [storedValue] — no interaction verification needed.
 */
class FakePropertyRepository : PropertyRepository {
    private val store = mutableMapOf<String, String>()

    fun givenProperty(key: String, value: String) {
        store[key] = value
    }

    fun storedValue(key: String): String? = store[key]

    override fun upsert(key: String, value: String, ttl: Long?): Either<PropertyRepositoryError, Unit> {
        store[key] = value
        return Unit.right()
    }

    override fun upsertBatch(entries: List<PropertyUpsertEntry>): Either<PropertyRepositoryError, Unit> {
        entries.forEach { store[it.key] = it.value }
        return Unit.right()
    }

    override fun findEntry(key: String): Either<FindPropertyFailure, PropertyEntry?> =
        store[key]?.let { PropertyEntry(it) }.right()

    override fun getEntry(key: String): Either<GetPropertyFailure, PropertyEntry> =
        store[key]?.let { Either.Right(PropertyEntry(it)) } ?: Either.Left(PropertyNotFound)
}
