package org.agrfesta.sh.api.core.application.ports.outbounds.settings

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.commons.PropertyUpsertEntry
import org.agrfesta.sh.api.core.domain.failures.FindPropertyFailure
import org.agrfesta.sh.api.core.domain.failures.GetPropertyFailure
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError

/**
 * Outbound Port for property persistence operations.
 */
interface PropertyRepository {

    /**
     * Inserts or updates a property entry identified by [key].
     *
     * @param key the unique key for the property entry.
     * @param value the value to store.
     * @param ttl optional time-to-live in seconds; `null` means no expiry.
     * @return [arrow.core.Either.Right] with [Unit] on success,
     * or [Either.Left] with [PropertyRepositoryError] if a database error occurs.
     */
    fun upsert(key: String, value: String, ttl: Long? = null): Either<PropertyRepositoryError, Unit>

    /**
     * Inserts or updates multiple property entries in a single batch operation.
     *
     * @param entries the list of [PropertyUpsertEntry] to persist.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [PropertyRepositoryError] if a database error occurs.
     */
    fun upsertBatch(entries: List<PropertyUpsertEntry>): Either<PropertyRepositoryError, Unit>

    /**
     * Looks up a property entry by [key] without failing if it does not exist.
     *
     * @param key the unique key to search for.
     * @return [Either.Right] with the [PropertyEntry] if found, or `null` if no entry exists for that key,
     * or [Either.Left] with [FindPropertyFailure] if a database error occurs.
     */
    fun findEntry(key: String): Either<FindPropertyFailure, PropertyEntry?>

    /**
     * Retrieves a property entry by [key], failing if it does not exist.
     *
     * @param key the unique key to retrieve.
     * @return [Either.Right] with the [PropertyEntry] if found,
     * or [Either.Left] with [GetPropertyFailure] if the entry does not exist or a database error occurs.
     */
    fun getEntry(key: String): Either<GetPropertyFailure, PropertyEntry>
}
