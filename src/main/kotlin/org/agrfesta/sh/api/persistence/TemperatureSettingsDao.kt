package org.agrfesta.sh.api.persistence

import arrow.core.Either
import java.util.*
import org.agrfesta.sh.api.domain.AreaTemperatureSetting
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreaNotFoundException

interface TemperatureSettingsDao {

    /**
     * Creates a new temperature setting for a specified area.
     * This method generates a unique UUID for the new temperature setting and checks whether a setting already exists
     * for the given area.
     * If a setting exists, it is deleted before saving the new one.
     * After saving the temperature setting, it also creates and saves corresponding temperature intervals associated
     * with the setting.
     * Ensures that either all operations complete successfully or none do.
     *
     * @param setting The [AreaTemperatureSetting] object containing the area ID, default temperature, and intervals to
     * be saved.
     * @return The [UUID] of the newly created temperature setting.
     * @throws AreaNotFoundException if the specified area does not exist.
     */
    fun createSetting(setting: AreaTemperatureSetting): UUID

    fun findAreaSetting(areaId: UUID): Either<PersistenceFailure, AreaTemperatureSetting?>

}
