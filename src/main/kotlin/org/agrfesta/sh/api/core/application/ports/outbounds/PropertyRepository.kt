package org.agrfesta.sh.api.core.application.ports.outbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.failures.FindPropertyFailure
import org.agrfesta.sh.api.core.domain.failures.GetPropertyFailure
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.PropertyEntryDto

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
     * or [Either.Left] with [PersistenceFailure] if a database error occurs.
     */
    fun upsert(key: String, value: String, ttl: Long? = null): Either<PersistenceFailure, Unit>

    /**
     * Inserts or updates multiple property entries in a single batch operation.
     *
     * @param entries the list of [PropertyEntryDto] to persist.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [PersistenceFailure] if a database error occurs.
     */
    fun upsertBatch(entries: List<PropertyEntryDto>): Either<PersistenceFailure, Unit>

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
