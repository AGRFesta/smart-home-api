package org.agrfesta.sh.api.services.heating

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import java.util.UUID
import org.agrfesta.sh.api.domain.areas.AreaTemperatureSetting
import org.agrfesta.sh.api.domain.areas.hasOverlap
import org.agrfesta.sh.api.domain.failures.OverlappingIntervals
import org.agrfesta.sh.api.domain.failures.TemperatureSettingCreationFailure
import org.agrfesta.sh.api.domain.failures.TemperatureSettingDeletionFailure
import org.agrfesta.sh.api.domain.failures.TemperatureSettingRetrievalFailure
import org.agrfesta.sh.api.domain.UnitOfWork
import org.agrfesta.sh.api.persistence.AreasDao
import org.agrfesta.sh.api.persistence.TemperatureSettingsDao
import org.springframework.stereotype.Service

/**
 * Service responsible for managing temperature settings for heatable areas.
 *
 * It provides operations to create, retrieve, and delete temperature configurations
 * associated with specific areas, handling persistence and domain validation.
 */
@Service
class HeatingAreasService(
    private val areasDao: AreasDao,
    private val temperatureSettingsDao: TemperatureSettingsDao,
    private val unitOfWork: UnitOfWork
) {

    /**
     * Creates a new temperature setting for a specific area.
     *
     * @param setting The temperature setting details to persist.
     * @return [Either.Right] with the UUID of the created setting, or [Either.Left] containing
     * a [TemperatureSettingCreationFailure] (e.g., [AreaNotFound] if the area doesn't exist).
     */
    fun createSetting(setting: AreaTemperatureSetting): Either<TemperatureSettingCreationFailure, Unit> {
        if (setting.temperatureSchedule.hasOverlap()) return OverlappingIntervals.left()
        return unitOfWork.execute {
            temperatureSettingsDao.existsByAreaId(setting.areaId)
                .flatMap { exists ->
                    if (exists) temperatureSettingsDao.deleteAreaSetting(setting.areaId)
                    else Unit.right()
                }
                .flatMap {
                    temperatureSettingsDao.persistAreaTemperatureSetting(setting)
                }
        }
    }

    /**
     * Deletes the temperature setting associated with the given area ID.
     *
     * It first verifies that the area exists before attempting deletion.
     *
     * @param areaId The unique identifier of the area.
     * @return [Either.Right] if deletion is successful, or [Either.Left] containing
     * a [TemperatureSettingDeletionFailure] (e.g., [AreaNotFound]).
     */
    fun deleteSetting(areaId: UUID): Either<TemperatureSettingDeletionFailure, Unit> =
        areasDao.getAreaById(areaId).flatMap {
            temperatureSettingsDao.deleteAreaSetting(areaId)
        }

    /**
     * Retrieves the temperature setting for a specific area.
     *
     * It first verifies that the area exists before attempting retrieval.
     *
     * @param areaId The unique identifier of the area.
     * @return [Either.Right] containing the [AreaTemperatureSetting] if found (or null),
     * or [Either.Left] with a [TemperatureSettingRetrievalFailure] in case of errors.
     */
    fun findAreaSetting(areaId: UUID): Either<TemperatureSettingRetrievalFailure, AreaTemperatureSetting?> =
        areasDao.getAreaById(areaId).flatMap {
            temperatureSettingsDao.findAreaSetting(areaId)
        }

}
