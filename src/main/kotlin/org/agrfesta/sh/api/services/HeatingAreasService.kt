package org.agrfesta.sh.api.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.*
import org.agrfesta.sh.api.domain.AreaTemperatureSetting
import org.agrfesta.sh.api.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.failures.TemperatureSettingCreationFailure
import org.agrfesta.sh.api.persistence.TemperatureSettingsDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreaNotFoundException
import org.springframework.stereotype.Service

@Service
class HeatingAreasService(
    private val temperatureSettingsDao: TemperatureSettingsDao
) {

    fun createSetting(setting: AreaTemperatureSetting): Either<TemperatureSettingCreationFailure, UUID> = try {
            temperatureSettingsDao.createSetting(setting).right()
        } catch (e: AreaNotFoundException) {
            AreaNotFound.left()
        } catch (e: Exception) {
            PersistenceFailure(e).left()
        }

}
