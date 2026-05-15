package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.*
import org.agrfesta.sh.api.core.domain.areas.AreaTemperatureSetting
import org.agrfesta.sh.api.core.domain.areas.TemperatureInterval
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.HeatingScheduleRepositoryError
import org.agrfesta.sh.api.core.domain.failures.TemperatureSettingCreationFailure
import org.agrfesta.sh.api.persistence.AreaNotFoundException
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.persistence.jdbc.entities.TemperatureIntervalEntity
import org.agrfesta.sh.api.persistence.jdbc.entities.TemperatureSettingEntity
import org.agrfesta.sh.api.persistence.jdbc.repositories.TemperatureIntervalRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.TemperatureSettingRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class TemperatureSettingsJdbcAdapter(
    private val tempSettingsRepository: TemperatureSettingRepository,
    private val tempIntervalsRepo: TemperatureIntervalRepository
): TemperatureSettingsRepository {
    private val logger by LoggerDelegate()

    override fun existsByAreaId(areaId: UUID): Either<HeatingScheduleRepositoryError, Boolean> = try {
        tempSettingsRepository.existsSettingByAreaId(areaId).right()
    } catch (e: DataAccessException) {
        logger.error("Failed to check temperature setting existence for area '$areaId'", e)
        HeatingScheduleRepositoryError.left()
    }

    override fun persistAreaTemperatureSetting(setting: AreaTemperatureSetting):
            Either<TemperatureSettingCreationFailure, Unit> {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            logger.error("persistAreaTemperatureSetting must be called within an active transaction")
            return HeatingScheduleRepositoryError.left()
        }
        return try {
            tempSettingsRepository.save(TemperatureSettingEntity(
                areaUuid = setting.areaId,
                defaultTemperature = setting.defaultTemperature.value
            ))
            setting.temperatureSchedule.map {
                TemperatureIntervalEntity(
                    uuid = UUID.randomUUID(),
                    areaUuid = setting.areaId,
                    startTime = it.startTime,
                    endTime = it.endTime,
                    temperature = it.temperature.value
                )
            }.forEach { tempIntervalsRepo.save(it) }
            Unit.right()
        } catch (_: AreaNotFoundException) {
            AreaNotFound(setting.areaId).left()
        } catch (e: DataAccessException) {
            logger.error("Failed to persist temperature setting for area '${setting.areaId}'", e)
            HeatingScheduleRepositoryError.left()
        }
    }

    @Transactional
    override fun createSetting(setting: AreaTemperatureSetting): Either<TemperatureSettingCreationFailure, Unit> = try {
        if (areaTempSettingAlreadyExist(setting.areaId)) {
            tempSettingsRepository.deleteByByAreaId(setting.areaId)
        }
        tempSettingsRepository.save(TemperatureSettingEntity(
            areaUuid = setting.areaId,
            defaultTemperature = setting.defaultTemperature.value
        ))
        setting.temperatureSchedule.map {
            TemperatureIntervalEntity(
                uuid = UUID.randomUUID(),
                areaUuid = setting.areaId,
                startTime = it.startTime,
                endTime = it.endTime,
                temperature = it.temperature.value
            )
        }.forEach { tempIntervalsRepo.save(it) }
        Unit.right()
    } catch (_: AreaNotFoundException) {
        AreaNotFound(setting.areaId).left()
    } catch (e: DataAccessException) {
        logger.error("Failed to create temperature setting for area '${setting.areaId}'", e)
        HeatingScheduleRepositoryError.left()
    }

    override fun findAreaSetting(areaId: UUID): Either<HeatingScheduleRepositoryError, AreaTemperatureSetting?> = try {
        tempSettingsRepository.findSettingByAreaId(areaId)?.let { setting ->
            val intervals = tempIntervalsRepo.findAllByArea(setting.areaUuid)
            AreaTemperatureSetting(
                areaId = setting.areaUuid,
                defaultTemperature = Temperature.of(setting.defaultTemperature),
                temperatureSchedule = intervals.map {
                    TemperatureInterval(
                        temperature = Temperature.of(it.temperature),
                        startTime = it.startTime,
                        endTime = it.endTime
                    )
                }.toSet()
            )
        }.right()
    } catch (e: DataAccessException) {
        logger.error("Failed to find temperature setting for area '$areaId'", e)
        HeatingScheduleRepositoryError.left()
    }

    override fun deleteAreaSetting(areaId: UUID): Either<HeatingScheduleRepositoryError, Unit> = try {
        tempSettingsRepository.deleteByByAreaId(areaId).right()
    } catch (e: DataAccessException) {
        logger.error("Failed to delete temperature setting for area '$areaId'", e)
        HeatingScheduleRepositoryError.left()
    }

    private fun areaTempSettingAlreadyExist(areaId: UUID): Boolean = try {
        tempSettingsRepository.existsSettingByAreaId(areaId)
    } catch (e: Exception) {
        logger.error("check area setting failure", e)
        false
    }

}
