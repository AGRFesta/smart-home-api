package org.agrfesta.sh.api.persistence.jdbc.adapters

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.domain.anAreaTemperatureSetting
import org.agrfesta.sh.api.domain.aTemperatureInterval
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.HeatingScheduleRepositoryError
import org.agrfesta.test.mothers.aRandomTemperature
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

class TemperatureSettingsJdbcAdapterTest : AbstractJdbcAdapterTest() {

    @Autowired private lateinit var sut: TemperatureSettingsJdbcAdapter

    // createSetting

    @Test
    fun `createSetting() Returns AreaNotFound when area is missing`() {
        val setting = anAreaTemperatureSetting(areaId = UUID.randomUUID())

        sut.createSetting(setting)
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNotFound>()
            .missingAreaId shouldBe setting.areaId
    }

    @Test
    fun `createSetting() Creates setting without intervals`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        val setting = anAreaTemperatureSetting(areaId = area.uuid, temperatureSchedule = emptySet())

        sut.createSetting(setting).shouldBeRight()

        val saved = tempSettingsRepo.findSettingByAreaId(area.uuid).shouldNotBeNull()
        saved.areaUuid shouldBe area.uuid
        saved.defaultTemperature shouldBe setting.defaultTemperature.value
        tempIntervalsRepo.findAllByArea(area.uuid).shouldBeEmpty()
    }

    @Test
    fun `createSetting() Creates setting with intervals`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        val interval1 = aTemperatureInterval()
        val interval2 = aTemperatureInterval()
        val setting = anAreaTemperatureSetting(areaId = area.uuid, temperatureSchedule = setOf(interval1, interval2))

        sut.createSetting(setting).shouldBeRight()

        val intervals = tempIntervalsRepo.findAllByArea(area.uuid)
        intervals shouldHaveSize 2
    }

    @Test
    fun `createSetting() Replaces existing setting for area`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        val originalSetting = anAreaTemperatureSetting(
            areaId = area.uuid,
            temperatureSchedule = setOf(aTemperatureInterval())
        )
        sut.createSetting(originalSetting).shouldBeRight()

        val newTemperature = aRandomTemperature()
        val newSetting = anAreaTemperatureSetting(
            areaId = area.uuid,
            defaultTemperature = newTemperature,
            temperatureSchedule = emptySet()
        )

        sut.createSetting(newSetting).shouldBeRight()

        val saved = tempSettingsRepo.findSettingByAreaId(area.uuid).shouldNotBeNull()
        saved.defaultTemperature shouldBe newTemperature.value
        tempIntervalsRepo.findAllByArea(area.uuid).shouldBeEmpty()
    }

    // findAreaSetting

    @Test
    fun `findAreaSetting() Returns null when no setting exists for area`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }

        sut.findAreaSetting(area.uuid)
            .shouldBeRight()
            .shouldBeNull()
    }

    @Test
    fun `findAreaSetting() Returns setting with no intervals`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        val setting = anAreaTemperatureSetting(areaId = area.uuid, temperatureSchedule = emptySet())
        sut.createSetting(setting).shouldBeRight()

        val result = sut.findAreaSetting(area.uuid)
            .shouldBeRight()
            .shouldNotBeNull()

        result.areaId shouldBe area.uuid
        result.defaultTemperature shouldBe setting.defaultTemperature
        result.temperatureSchedule.shouldBeEmpty()
    }

    @Test
    fun `findAreaSetting() Returns setting with intervals`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        val interval1 = aTemperatureInterval()
        val interval2 = aTemperatureInterval()
        val setting = anAreaTemperatureSetting(areaId = area.uuid, temperatureSchedule = setOf(interval1, interval2))
        sut.createSetting(setting).shouldBeRight()

        val result = sut.findAreaSetting(area.uuid)
            .shouldBeRight()
            .shouldNotBeNull()

        result.areaId shouldBe area.uuid
        result.defaultTemperature shouldBe setting.defaultTemperature
        result.temperatureSchedule shouldHaveSize 2
    }

    // deleteAreaSetting

    @Test
    fun `deleteAreaSetting() Returns HeatingScheduleRepositoryError on database error`() {
        every { tempSettingsRepo.deleteByByAreaId(any()) } throws object : DataAccessException("db error") {}

        sut.deleteAreaSetting(UUID.randomUUID())
            .shouldBeLeft()
            .shouldBe(HeatingScheduleRepositoryError)
    }

    @Test
    fun `deleteAreaSetting() Deletes existing setting`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        val setting = anAreaTemperatureSetting(areaId = area.uuid, temperatureSchedule = setOf(aTemperatureInterval()))
        sut.createSetting(setting).shouldBeRight()

        sut.deleteAreaSetting(area.uuid).shouldBeRight()

        tempSettingsRepo.findSettingByAreaId(area.uuid).shouldBeNull()
    }

    @Test
    fun `deleteAreaSetting() Succeeds when no setting exists for area`() {
        val missingAreaId = UUID.randomUUID()

        sut.deleteAreaSetting(missingAreaId).shouldBeRight()
    }

    // existsByAreaId

    @Test
    fun `existsByAreaId() Returns HeatingScheduleRepositoryError on database error`() {
        every { tempSettingsRepo.existsSettingByAreaId(any()) } throws object : DataAccessException("db error") {}

        sut.existsByAreaId(UUID.randomUUID())
            .shouldBeLeft()
            .shouldBe(HeatingScheduleRepositoryError)
    }

    @Test
    fun `existsByAreaId() Returns false when no setting exists for area`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }

        sut.existsByAreaId(area.uuid)
            .shouldBeRight()
            .shouldBe(false)
    }

    @Test
    fun `existsByAreaId() Returns true when setting exists for area`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        val setting = anAreaTemperatureSetting(areaId = area.uuid, temperatureSchedule = emptySet())
        sut.createSetting(setting).shouldBeRight()

        sut.existsByAreaId(area.uuid)
            .shouldBeRight()
            .shouldBe(true)
    }

    @Test
    fun `existsByAreaId() Returns false when area does not exist in database`() {
        val nonExistentAreaId = UUID.randomUUID()

        sut.existsByAreaId(nonExistentAreaId)
            .shouldBeRight()
            .shouldBe(false)
    }

    // persistAreaTemperatureSetting

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `persistAreaTemperatureSetting() Returns HeatingScheduleRepositoryError when called outside active transaction`() {
        val setting = anAreaTemperatureSetting(areaId = UUID.randomUUID())

        sut.persistAreaTemperatureSetting(setting)
            .shouldBeLeft()
            .shouldBe(HeatingScheduleRepositoryError)
    }

    @Test
    fun `persistAreaTemperatureSetting() Persists setting root without intervals`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        val setting = anAreaTemperatureSetting(areaId = area.uuid, temperatureSchedule = emptySet())

        sut.persistAreaTemperatureSetting(setting).shouldBeRight()

        val saved = tempSettingsRepo.findSettingByAreaId(area.uuid).shouldNotBeNull()
        saved.areaUuid shouldBe area.uuid
        saved.defaultTemperature shouldBe setting.defaultTemperature.value
        tempIntervalsRepo.findAllByArea(area.uuid).shouldBeEmpty()
    }

    @Test
    fun `persistAreaTemperatureSetting() Persists setting root and intervals`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        val interval1 = aTemperatureInterval()
        val interval2 = aTemperatureInterval()
        val setting = anAreaTemperatureSetting(areaId = area.uuid, temperatureSchedule = setOf(interval1, interval2))

        sut.persistAreaTemperatureSetting(setting).shouldBeRight()

        val saved = tempSettingsRepo.findSettingByAreaId(area.uuid).shouldNotBeNull()
        saved.areaUuid shouldBe area.uuid
        saved.defaultTemperature shouldBe setting.defaultTemperature.value
        tempIntervalsRepo.findAllByArea(area.uuid) shouldHaveSize 2
    }

    @Test
    fun `persistAreaTemperatureSetting() Returns AreaNotFound when area does not exist`() {
        val missingAreaId = UUID.randomUUID()
        val setting = anAreaTemperatureSetting(areaId = missingAreaId)

        sut.persistAreaTemperatureSetting(setting)
            .shouldBeLeft()
            .shouldBeInstanceOf<AreaNotFound>()
            .missingAreaId shouldBe missingAreaId
    }

    @Test
    fun `persistAreaTemperatureSetting() Returns HeatingScheduleRepositoryError on database error while persisting root`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        val setting = anAreaTemperatureSetting(areaId = area.uuid, temperatureSchedule = emptySet())
        every { tempSettingsRepo.save(any()) } throws object : DataAccessException("db error") {}

        sut.persistAreaTemperatureSetting(setting)
            .shouldBeLeft()
            .shouldBe(HeatingScheduleRepositoryError)
    }

    @Test
    fun `persistAreaTemperatureSetting() Returns HeatingScheduleRepositoryError on database error while persisting an interval`() {
        every { timeProvider.now() } returns Instant.now()
        val area = anAreaDto(name = aRandomUniqueString()).also { areasRepo.persist(it) }
        val setting = anAreaTemperatureSetting(areaId = area.uuid, temperatureSchedule = setOf(aTemperatureInterval()))
        every { tempIntervalsRepo.save(any()) } throws object : DataAccessException("db error") {}

        sut.persistAreaTemperatureSetting(setting)
            .shouldBeLeft()
            .shouldBe(HeatingScheduleRepositoryError)
    }
}
