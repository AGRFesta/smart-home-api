package org.agrfesta.sh.api.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.*
import org.agrfesta.sh.api.domain.AreaTemperatureSetting
import org.agrfesta.sh.api.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.domain.failures.TemperatureSettingCreationFailure
import org.agrfesta.sh.api.domain.failures.TemperatureSettingDeletionFailure
import org.agrfesta.sh.api.persistence.AreaNotFoundException
import org.agrfesta.sh.api.persistence.TemperatureSettingsDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasJdbcRepository
import org.springframework.stereotype.Service

@Service
class HeatingAreasService(
    private val areasRepository: AreasJdbcRepository,
    private val temperatureSettingsDao: TemperatureSettingsDao
) {

    fun createSetting(setting: AreaTemperatureSetting): Either<TemperatureSettingCreationFailure, UUID> = try {
        temperatureSettingsDao.createSetting(setting).right()
    } catch (e: AreaNotFoundException) {
        AreaNotFound.left()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    fun deleteSetting(areaId: UUID): Either<TemperatureSettingDeletionFailure, Unit> = try {
        areasRepository.getAreaById(areaId)
        temperatureSettingsDao.deleteAreaSetting(areaId).right()
    } catch (e: AreaNotFoundException) {
        AreaNotFound.left()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

    fun findAreaSetting(areaId: UUID): Either<PersistenceFailure, AreaTemperatureSetting?> = try {
        temperatureSettingsDao.findAreaSetting(areaId).right()
    } catch (e: Exception) {
        PersistenceFailure(e).left()
    }

}
