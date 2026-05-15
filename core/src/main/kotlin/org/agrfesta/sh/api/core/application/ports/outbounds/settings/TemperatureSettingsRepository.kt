package org.agrfesta.sh.api.core.application.ports.outbounds.settings

import arrow.core.Either
import java.util.UUID
import org.agrfesta.sh.api.core.domain.areas.AreaTemperatureSetting
import org.agrfesta.sh.api.core.domain.failures.HeatingScheduleRepositoryError
import org.agrfesta.sh.api.core.domain.failures.TemperatureSettingCreationFailure

interface TemperatureSettingsRepository {

    /**
     * Checks whether a temperature setting exists for the given area.
     *
     * @param areaId the unique identifier of the area.
     * @return [arrow.core.Either.Right] with `true` if a setting exists, `false` otherwise,
     * or [Either.Left] with [HeatingScheduleRepositoryError] if a database error occurs.
     */
    fun existsByAreaId(areaId: UUID): Either<HeatingScheduleRepositoryError, Boolean>

    /**
     * Persists the temperature setting (root entry and intervals) for an area within an already active transaction.
     *
     * **Requires an active transaction.** Calling this method outside a transaction boundary returns
     * [Either.Left] with [HeatingScheduleRepositoryError] immediately, without touching the database.
     *
     * @param setting The [AreaTemperatureSetting] containing the area ID, default temperature, and intervals.
     * @return [Either.Right] with [Unit] on success, or [Either.Left] with [TemperatureSettingCreationFailure]
     * if the area does not exist or a persistence error occurs.
     */
    fun persistAreaTemperatureSetting(setting: AreaTemperatureSetting): Either<TemperatureSettingCreationFailure, Unit>

    /**
     * Creates a new temperature setting for a specified area.
     * If a setting already exists for the area, it is replaced.
     *
     * @param setting The [AreaTemperatureSetting] object containing the area ID, default temperature, and intervals.
     * @return [Either.Right] with [Unit] on success, or [Either.Left] with [TemperatureSettingCreationFailure]
     * if the area does not exist or a persistence error occurs.
     */
    fun createSetting(setting: AreaTemperatureSetting): Either<TemperatureSettingCreationFailure, Unit>

    /**
     * Retrieves the temperature setting for a specific area.
     *
     * @param areaId the unique identifier of the area.
     * @return [Either.Right] with the [AreaTemperatureSetting] if found, or `null` if no setting exists,
     * or [Either.Left] with [HeatingScheduleRepositoryError] if a database error occurs.
     */
    fun findAreaSetting(areaId: UUID): Either<HeatingScheduleRepositoryError, AreaTemperatureSetting?>

    /**
     * Deletes the temperature setting for a specific area.
     *
     * @param areaId the unique identifier of the area.
     * @return [Either.Right] with [Unit] on success,
     * or [Either.Left] with [HeatingScheduleRepositoryError] if a database error occurs.
     */
    fun deleteAreaSetting(areaId: UUID): Either<HeatingScheduleRepositoryError, Unit>

}
