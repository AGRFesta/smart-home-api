package org.agrfesta.sh.api.persistence

import arrow.core.Either
import java.util.UUID
import org.agrfesta.sh.api.domain.areas.AreaDto
import org.agrfesta.sh.api.domain.failures.AreaCreationFailure
import org.agrfesta.sh.api.domain.failures.AreaDeletionFailure
import org.agrfesta.sh.api.domain.failures.AreaFetchFailure
import org.agrfesta.sh.api.domain.failures.PersistenceFailure

/**
 * Data access object for [AreaDto] persistence operations.
 *
 * All operations return an [Either] type: [Either.Right] on success, [Either.Left] on failure.
 * Failures are typed and represent either domain-level errors (e.g. not found, name conflict)
 * or infrastructure-level errors (e.g. database access failure).
 */
interface AreasDao {

    /**
     * Retrieves an [AreaDto] by its unique identifier.
     *
     * @param areaId the unique identifier of the area to retrieve.
     * @return [Either.Right] with the [AreaDto] if found,
     * or [Either.Left] with [AreaFetchFailure] if the area does not exist or a persistence error occurs.
     */
    fun getAreaById(areaId: UUID): Either<AreaFetchFailure, AreaDto>

    /**
     * Looks up an [AreaDto] by name without failing if it does not exist.
     *
     * @param name the name of the area to search for.
     * @return [Either.Right] with the [AreaDto] if found, or `null` if no area with that name exists,
     * or [Either.Left] with [PersistenceFailure] if a database error occurs.
     */
    fun findAreaByName(name: String): Either<PersistenceFailure, AreaDto?>

    /**
     * Persists a new [AreaDto].
     *
     * @param area the area to save.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [AreaCreationFailure] if the area could not be saved
     * (e.g. an area with the same name already exists, or a persistence error occurs).
     */
    fun save(area: AreaDto): Either<AreaCreationFailure, Unit>

    /**
     * Retrieves all persisted areas.
     *
     * @return [Either.Right] with a collection of all [AreaDto] instances,
     * or [Either.Left] with [PersistenceFailure] if a database error occurs.
     */
    fun getAll(): Either<PersistenceFailure, Collection<AreaDto>>

    /**
     * Deletes an [AreaDto] by its unique identifier.
     *
     * @param areaId the unique identifier of the area to delete.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [AreaDeletionFailure] if the area does not exist or a persistence error occurs.
     */
    fun deleteAreaById(areaId: UUID): Either<AreaDeletionFailure, Unit>

}
