package org.agrfesta.sh.api.services.heating

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.domain.anAreaTemperatureSetting
import org.agrfesta.sh.api.domain.aTemperatureInterval
import org.agrfesta.sh.api.domain.failures.AreaNotFound
import org.agrfesta.sh.api.domain.failures.OverlappingIntervals
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.AreasDao
import org.agrfesta.sh.api.persistence.TemperatureSettingsDao
import org.agrfesta.sh.api.persistence.utils.TransactionRunner
import org.agrfesta.test.mothers.aDailyTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class HeatingAreasServiceTest {
    private val areasDao: AreasDao = mockk()
    private val temperatureSettingsDao: TemperatureSettingsDao = mockk()
    private val transactionRunner: TransactionRunner = mockk()

    private val sut = HeatingAreasService(areasDao, temperatureSettingsDao, transactionRunner)

    @BeforeEach
    fun setUp() {
        every { transactionRunner.executeInTransaction(any<() -> Either<Any, Any>>()) } answers { call ->
            @Suppress("UNCHECKED_CAST")
            (call.invocation.args[0] as () -> Either<Any, Any>)()
        }
    }

    // createSetting()

    @TestFactory
    fun `createSetting() Returns OverlappingIntervals when schedule has overlapping intervals`() = listOf(
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 0), endTime = aDailyTime(hour = 9)),
            aTemperatureInterval(startTime = aDailyTime(hour = 5), endTime = aDailyTime(hour = 6))
        ),
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 22), endTime = aDailyTime(hour = 3)),
            aTemperatureInterval(startTime = aDailyTime(hour = 10), endTime = aDailyTime(hour = 23))
        ),
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 22), endTime = aDailyTime(hour = 3)),
            aTemperatureInterval(startTime = aDailyTime(hour = 1), endTime = aDailyTime(hour = 17))
        ),
        listOf(
            aTemperatureInterval(startTime = aDailyTime(hour = 1), endTime = aDailyTime(hour = 22)),
            aTemperatureInterval(startTime = aDailyTime(hour = 3), endTime = aDailyTime(hour = 20)),
            aTemperatureInterval(startTime = aDailyTime(hour = 5), endTime = aDailyTime(hour = 6))
        )
    ).map { intervals ->
        dynamicTest("$intervals") {
            val setting = anAreaTemperatureSetting(temperatureSchedule = intervals.toSet())

            sut.createSetting(setting).shouldBeLeft() shouldBe OverlappingIntervals
        }
    }

    @Test
    fun `createSetting() Does not call dao when intervals overlap`() {
        val setting = anAreaTemperatureSetting(
            temperatureSchedule = setOf(
                aTemperatureInterval(startTime = aDailyTime(hour = 0), endTime = aDailyTime(hour = 9)),
                aTemperatureInterval(startTime = aDailyTime(hour = 5), endTime = aDailyTime(hour = 6))
            )
        )

        sut.createSetting(setting)

        verify(exactly = 0) { temperatureSettingsDao.existsByAreaId(any()) }
        verify(exactly = 0) { temperatureSettingsDao.deleteAreaSetting(any()) }
        verify(exactly = 0) { temperatureSettingsDao.persistAreaTemperatureSetting(any()) }
    }

    @Test
    fun `createSetting() Returns Unit on success when no prior setting exists`() {
        val setting = anAreaTemperatureSetting()
        every { temperatureSettingsDao.existsByAreaId(setting.areaId) } returns false.right()
        every { temperatureSettingsDao.persistAreaTemperatureSetting(setting) } returns Unit.right()

        sut.createSetting(setting).shouldBeRight()
    }

    @Test
    fun `createSetting() Returns Unit on success when prior setting already exists`() {
        val setting = anAreaTemperatureSetting()
        every { temperatureSettingsDao.existsByAreaId(setting.areaId) } returns true.right()
        every { temperatureSettingsDao.deleteAreaSetting(setting.areaId) } returns Unit.right()
        every { temperatureSettingsDao.persistAreaTemperatureSetting(setting) } returns Unit.right()

        sut.createSetting(setting).shouldBeRight()
    }

    @Test
    fun `createSetting() Does not call deleteAreaSetting when no prior setting exists`() {
        val setting = anAreaTemperatureSetting()
        every { temperatureSettingsDao.existsByAreaId(setting.areaId) } returns false.right()
        every { temperatureSettingsDao.persistAreaTemperatureSetting(setting) } returns Unit.right()

        sut.createSetting(setting)

        verify(exactly = 0) { temperatureSettingsDao.deleteAreaSetting(any()) }
    }

    @Test
    fun `createSetting() Returns PersistenceFailure when existsByAreaId fails`() {
        val setting = anAreaTemperatureSetting()
        every { temperatureSettingsDao.existsByAreaId(setting.areaId) } returns
            PersistenceFailure(Exception("db error")).left()

        sut.createSetting(setting).shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `createSetting() Returns PersistenceFailure when deleteAreaSetting fails`() {
        val setting = anAreaTemperatureSetting()
        every { temperatureSettingsDao.existsByAreaId(setting.areaId) } returns true.right()
        every { temperatureSettingsDao.deleteAreaSetting(setting.areaId) } returns
            PersistenceFailure(Exception("db error")).left()

        sut.createSetting(setting).shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `createSetting() Returns PersistenceFailure when persistAreaTemperatureSetting fails`() {
        val setting = anAreaTemperatureSetting()
        every { temperatureSettingsDao.existsByAreaId(setting.areaId) } returns false.right()
        every { temperatureSettingsDao.persistAreaTemperatureSetting(setting) } returns
            PersistenceFailure(Exception("db error")).left()

        sut.createSetting(setting).shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `createSetting() Returns AreaNotFound when persistAreaTemperatureSetting returns AreaNotFound`() {
        val setting = anAreaTemperatureSetting()
        val failure = AreaNotFound(setting.areaId)
        every { temperatureSettingsDao.existsByAreaId(setting.areaId) } returns false.right()
        every { temperatureSettingsDao.persistAreaTemperatureSetting(setting) } returns failure.left()

        sut.createSetting(setting).shouldBeLeft() shouldBe failure
    }

    // deleteSetting()

    @Test
    fun `deleteSetting() Returns Unit on success`() {
        val areaId = UUID.randomUUID()
        every { areasDao.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsDao.deleteAreaSetting(areaId) } returns Unit.right()

        sut.deleteSetting(areaId).shouldBeRight()
    }

    @Test
    fun `deleteSetting() Returns AreaNotFound when area does not exist`() {
        val areaId = UUID.randomUUID()
        val failure = AreaNotFound(areaId)
        every { areasDao.getAreaById(areaId) } returns failure.left()

        sut.deleteSetting(areaId).shouldBeLeft() shouldBe failure
    }

    @Test
    fun `deleteSetting() Does not call deleteAreaSetting when area is not found`() {
        val areaId = UUID.randomUUID()
        every { areasDao.getAreaById(areaId) } returns AreaNotFound(areaId).left()

        sut.deleteSetting(areaId)

        verify(exactly = 0) { temperatureSettingsDao.deleteAreaSetting(any()) }
    }

    @Test
    fun `deleteSetting() Returns PersistenceFailure when areasDao fails`() {
        val areaId = UUID.randomUUID()
        every { areasDao.getAreaById(areaId) } returns PersistenceFailure(Exception("db error")).left()

        sut.deleteSetting(areaId).shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `deleteSetting() Returns PersistenceFailure when temperatureSettingsDao fails`() {
        val areaId = UUID.randomUUID()
        every { areasDao.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsDao.deleteAreaSetting(areaId) } returns
            PersistenceFailure(Exception("db error")).left()

        sut.deleteSetting(areaId).shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
    }

    // findAreaSetting()

    @Test
    fun `findAreaSetting() Returns setting when found`() {
        val areaId = UUID.randomUUID()
        val setting = anAreaTemperatureSetting(areaId = areaId)
        every { areasDao.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsDao.findAreaSetting(areaId) } returns setting.right()

        sut.findAreaSetting(areaId).shouldBeRight() shouldBe setting
    }

    @Test
    fun `findAreaSetting() Returns null when no setting exists for the area`() {
        val areaId = UUID.randomUUID()
        every { areasDao.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsDao.findAreaSetting(areaId) } returns null.right()

        sut.findAreaSetting(areaId).shouldBeRight() shouldBe null
    }

    @Test
    fun `findAreaSetting() Returns AreaNotFound when area does not exist`() {
        val areaId = UUID.randomUUID()
        val failure = AreaNotFound(areaId)
        every { areasDao.getAreaById(areaId) } returns failure.left()

        sut.findAreaSetting(areaId).shouldBeLeft() shouldBe failure
    }

    @Test
    fun `findAreaSetting() Does not call findAreaSetting on dao when area is not found`() {
        val areaId = UUID.randomUUID()
        every { areasDao.getAreaById(areaId) } returns AreaNotFound(areaId).left()

        sut.findAreaSetting(areaId)

        verify(exactly = 0) { temperatureSettingsDao.findAreaSetting(any()) }
    }

    @Test
    fun `findAreaSetting() Returns PersistenceFailure when areasDao fails`() {
        val areaId = UUID.randomUUID()
        every { areasDao.getAreaById(areaId) } returns PersistenceFailure(Exception("db error")).left()

        sut.findAreaSetting(areaId).shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
    }

    @Test
    fun `findAreaSetting() Returns PersistenceFailure when temperatureSettingsDao fails`() {
        val areaId = UUID.randomUUID()
        every { areasDao.getAreaById(areaId) } returns anAreaDto(uuid = areaId).right()
        every { temperatureSettingsDao.findAreaSetting(areaId) } returns
            PersistenceFailure(Exception("db error")).left()

        sut.findAreaSetting(areaId).shouldBeLeft().shouldBeInstanceOf<PersistenceFailure>()
    }

}
