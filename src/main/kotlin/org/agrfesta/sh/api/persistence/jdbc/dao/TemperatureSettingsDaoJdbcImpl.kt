package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import arrow.core.right
import java.util.*
import org.agrfesta.sh.api.domain.AreaTemperatureSetting
import org.agrfesta.sh.api.domain.TemperatureInterval
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.TemperatureSettingsDao
import org.agrfesta.sh.api.persistence.jdbc.entities.TemperatureIntervalEntity
import org.agrfesta.sh.api.persistence.jdbc.entities.TemperatureSettingEntity
import org.agrfesta.sh.api.persistence.jdbc.repositories.TemperatureIntervalRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.TemperatureSettingRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.agrfesta.sh.api.utils.RandomGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TemperatureSettingsDaoJdbcImpl(
    private val tempSettingsRepository: TemperatureSettingRepository,
    private val tempIntervalsRepo: TemperatureIntervalRepository,
    private val randomGenerator: RandomGenerator
): TemperatureSettingsDao {
    private val logger by LoggerDelegate()

    @Transactional
    override fun createSetting(setting: AreaTemperatureSetting): UUID {
        if (areaTempSettingAlreadyExist(setting.areaId)) {
            tempSettingsRepository.deleteByByAreaId(setting.areaId)
        }
        val settingsId = randomGenerator.uuid()
        val uuid = tempSettingsRepository.save(TemperatureSettingEntity(
            uuid = settingsId,
            areaUuid = setting.areaId,
            defaultTemperature = setting.defaultTemperature
        ))
        setting.temperatureSchedule.map {
            TemperatureIntervalEntity(
                uuid = randomGenerator.uuid(),
                settingUuid = settingsId,
                startTime = it.startTime,
                endTime = it.endTime,
                temperature = it.temperature
            )
        }.forEach { tempIntervalsRepo.save(it) }
        return uuid
    }

    override fun findAreaSetting(areaId: UUID): Either<PersistenceFailure, AreaTemperatureSetting?> =
        tempSettingsRepository.findSettingByAreaId(areaId)?.let { setting ->
            val intervals = tempIntervalsRepo.findAllBySetting(setting.uuid)
            AreaTemperatureSetting(
                areaId = setting.areaUuid,
                defaultTemperature = setting.defaultTemperature,
                temperatureSchedule = intervals.map {
                    TemperatureInterval(
                        temperature = it.temperature,
                        startTime = it.startTime,
                        endTime = it.endTime
                    )
                }.toSet()
            )
        }.right()

    private fun areaTempSettingAlreadyExist(areaId: UUID): Boolean = try {
        tempSettingsRepository.existsSettingByAreaId(areaId)
    } catch (e: Exception) {
        logger.error("check area setting failure", e)
        false
    }

}
