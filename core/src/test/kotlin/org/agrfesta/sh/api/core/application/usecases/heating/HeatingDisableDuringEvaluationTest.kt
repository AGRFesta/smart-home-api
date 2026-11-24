package org.agrfesta.sh.api.core.application.usecases.heating

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasWithDevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.FailureByException
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.application.usecases.heating.EvaluateHeatingStateService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.heating.HeaterCommand
import org.agrfesta.sh.api.core.domain.heating.HeatingDecider
import org.agrfesta.sh.api.domain.aSensor
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.domain.anAreaWithDevicesView
import org.agrfesta.sh.api.utils.FakePropertyRepository
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread

/** A heating disable requested while a heating evaluation is in progress takes effect after it. */
class HeatingDisableDuringEvaluationTest {
    private val propertyRepository = FakePropertyRepository()
    private val devicesRepository: DevicesRepository = mockk()
    private val factory: ProviderDevicesFactory = mockk {
        every { provider } returns Provider.SWITCHBOT
    }
    private val areasWithDevicesRepository: AreasWithDevicesRepository = mockk()
    private val temperatureSettingsRepository: TemperatureSettingsRepository = mockk()
    private val strategySelector: HeatingStrategySelector = mockk()
    private val decide: HeatingDecider = mockk()
    private val timeProvider: TimeProvider = mockk()
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher = mockk(relaxUnitFun = true)

    private val registeredDeviceDrivers = RegisteredDeviceDrivers(devicesRepository, listOf(factory))
    private val heatingActuationLock = HeatingActuationLock()
    private val evaluation = EvaluateHeatingStateService(
        registeredDeviceDrivers,
        areasWithDevicesRepository,
        temperatureSettingsRepository,
        strategySelector,
        propertyRepository,
        timeProvider,
        heatingActuationLock
    )
    private val disable = SetHeatingEnabledService(
        propertyRepository,
        homeStateRefreshPublisher,
        registeredDeviceDrivers,
        heatingActuationLock
    )

    init {
        every { timeProvider.currentLocalTime() } returns LocalTime.NOON
        every { temperatureSettingsRepository.findAreaSetting(any()) } returns null.right()
        every { strategySelector.select() } returns decide
    }

    @Test fun `a disable requested while an evaluation commands a heater sends OFF after the evaluation's ON`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "true")
        every { decide(any()) } returns HeaterCommand.ON
        val heater = givenHeaterInHeatableArea()
        val onInFlight = CountDownLatch(1)
        val releaseOn = CountDownLatch(1)
        heater.beforeOn = {
            onInFlight.countDown()
            releaseOn.await(5, SECONDS)
        }
        val evaluationThread = thread { evaluation.execute() }
        onInFlight.await(5, SECONDS) shouldBe true

        // When
        val disableThread = thread { disable.execute(false) }
        disableThread.join(500)
        releaseOn.countDown()
        evaluationThread.join(5_000)
        disableThread.join(5_000)

        // Then
        withClue("the disable should wait for the evaluation in progress, so the heater ends up OFF") {
            heater.commands shouldBe listOf(HeaterCommandSent.ON, HeaterCommandSent.OFF)
        }
    }

    private fun givenHeaterInHeatableArea(): FakeHeater {
        val sensorDto = aSensor()
        val sensor = sensorDto.toSensorMockk(factory)
        every { sensor.fetchReadings() } returns FailureByException(RuntimeException("unavailable")).left()
        val heater = FakeHeater()
        val heaterDto = anActuator(uuid = heater.uuid)
        every { factory.createDevice(heaterDto) } returns heater
        every { devicesRepository.getAll() } returns listOf(sensorDto, heaterDto).right()
        every { areasWithDevicesRepository.getAllAreasWithDevices() } returns
            listOf(anAreaWithDevicesView(sensors = listOf(sensorDto), actuators = listOf(heaterDto))).right()
        return heater
    }
}
