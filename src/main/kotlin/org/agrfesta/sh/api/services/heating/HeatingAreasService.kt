package org.agrfesta.sh.api.services.heating

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.UUID
import org.agrfesta.sh.api.domain.areas.AreaTemperatureSetting
import org.agrfesta.sh.api.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.failures.TemperatureSettingCreationFailure
import org.agrfesta.sh.api.domain.failures.TemperatureSettingDeletionFailure
import org.agrfesta.sh.api.persistence.AreaDao
import org.agrfesta.sh.api.persistence.AreaNotFoundException
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
    private val areasDao: AreaDao,
    private val temperatureSettingsDao: TemperatureSettingsDao
) {

    /**
     * Creates a new temperature setting for a specific area.
     *
     * @param setting The temperature setting details to persist.
     * @return [Either.Right] with the UUID of the created setting, or [Either.Left] containing
     * a [TemperatureSettingCreationFailure] (e.g., [AreaNotFound] if the area doesn't exist).
     */
    fun createSetting(setting: AreaTemperatureSetting): Either<TemperatureSettingCreationFailure, UUID> = try {
        temperatureSettingsDao.createSetting(setting).right()
    } catch (e: AreaNotFoundException) {
        AreaNotFound.left()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
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
    fun deleteSetting(areaId: UUID): Either<TemperatureSettingDeletionFailure, Unit> = try {
        areasDao.getAreaById(areaId)
        temperatureSettingsDao.deleteAreaSetting(areaId).right()
    } catch (e: AreaNotFoundException) {
        AreaNotFound.left()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    /**
     * Retrieves the temperature setting for a specific area.
     *
     * @param areaId The unique identifier of the area.
     * @return [Either.Right] containing the [AreaTemperatureSetting] if found (or null),
     * or [Either.Left] with a [PersistenceFailure] in case of errors.
     */
    fun findAreaSetting(areaId: UUID): Either<PersistenceFailure, AreaTemperatureSetting?> = try {
        temperatureSettingsDao.findAreaSetting(areaId).right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

}