package org.agrfesta.sh.api.core.application.usecases.heating

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.home.HomeStateRefreshPublisher
import org.agrfesta.sh.api.core.application.usecases.heating.EvaluateHeatingStateService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DevicesUnavailable
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.core.domain.heating.SetHeatingEnabledReport
import org.agrfesta.sh.api.domain.anActuator
import org.agrfesta.sh.api.utils.FakePropertyRepository
import org.junit.jupiter.api.Test

class SetHeatingEnabledServiceTest {
    private val propertyRepository = FakePropertyRepository()
    private val homeStateRefreshPublisher: HomeStateRefreshPublisher = mockk(relaxUnitFun = true)
    private val devicesRepository: DevicesRepository = mockk()
    private val factory: ProviderDevicesFactory = mockk {
        every { provider } returns Provider.SWITCHBOT
    }
    private val registeredDevices = mutableListOf<Device>()
    private val sut = SetHeatingEnabledService(
        propertyRepository,
        homeStateRefreshPublisher,
        RegisteredDeviceDrivers(devicesRepository, listOf(factory)),
        HeatingActuationLock()
    )

    init {
        every { devicesRepository.getAll() } answers { registeredDevices.toList().right() }
    }

    @Test fun `execute() saves heating_enabled as true when heating is enabled`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "false")

        // When
        val result = sut.execute(true)

        // Then
        result.shouldBeRight()
        withClue("heating.enabled should be saved as \"true\"") {
            propertyRepository.storedValue(HEATING_ENABLED_KEY) shouldBe "true"
        }
    }

    @Test fun `execute() saves heating_enabled as false when heating is disabled`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "true")

        // When
        val result = sut.execute(false)

        // Then
        result.shouldBeRight()
        withClue("heating.enabled should be saved as \"false\"") {
            propertyRepository.storedValue(HEATING_ENABLED_KEY) shouldBe "false"
        }
    }

    @Test fun `execute() publishes exactly one home state refresh after saving the flag`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "false")

        // When
        sut.execute(true)

        // Then
        verify(exactly = 1) { homeStateRefreshPublisher.publish() }
    }

    @Test fun `execute() returns PropertyRepositoryError, keeps the flag and does not publish when the save fails`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "true")
        propertyRepository.givenUpsertFails()

        // When
        val result = sut.execute(false)

        // Then
        result.shouldBeLeft() shouldBe PropertyRepositoryError
        withClue("heating.enabled should keep its previous value when the save fails") {
            propertyRepository.storedValue(HEATING_ENABLED_KEY) shouldBe "true"
        }
        withClue("connected clients should receive no update when nothing changed") {
            verify(exactly = 0) { homeStateRefreshPublisher.publish() }
        }
    }

    @Test fun `execute() sends OFF once to every registered heater when heating is disabled`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "true")
        val h1 = givenRegisteredHeater()
        val h2 = givenRegisteredHeater()

        // When
        val result = sut.execute(false)

        // Then
        result.shouldBeRight() shouldBe SetHeatingEnabledReport(failedHeaters = emptySet())
        withClue("heating.enabled should be saved as \"false\" before the heaters are turned off") {
            propertyRepository.storedValue(HEATING_ENABLED_KEY) shouldBe "false"
        }
        withClue("every registered heater should receive exactly one OFF") {
            h1.commands shouldBe listOf(HeaterCommandSent.OFF)
            h2.commands shouldBe listOf(HeaterCommandSent.OFF)
        }
    }

    @Test fun `execute() sends no command to the heaters when heating is enabled`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "false")
        val heater = givenRegisteredHeater()

        // When
        sut.execute(true)

        // Then
        withClue("enabling heating should leave the heaters to the next heating evaluation") {
            heater.commands shouldBe emptyList()
        }
    }

    @Test fun `execute() sends no command to the heaters when the flag cannot be saved`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "true")
        propertyRepository.givenUpsertFails()
        val heater = givenRegisteredHeater()

        // When
        val result = sut.execute(false)

        // Then
        result.shouldBeLeft() shouldBe PropertyRepositoryError
        withClue("a heater should stay as it is when heating.enabled still says true") {
            heater.commands shouldBe emptyList()
        }
    }

    @Test fun `execute() turns off the other heaters and reports the heater whose OFF fails`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "true")
        val h1 = givenRegisteredHeater()
        val h2 = givenRegisteredHeater()
        h2.givenOffFails()

        // When
        val result = sut.execute(false)

        // Then
        withClue("the heater whose OFF failed should be reported in failedHeaters") {
            result.shouldBeRight() shouldBe SetHeatingEnabledReport(failedHeaters = setOf(h2.uuid))
        }
        withClue("a failed OFF should not stop the OFF of the other heaters") {
            h1.commands shouldBe listOf(HeaterCommandSent.OFF)
        }
    }

    @Test fun `execute() turns off the other heaters and reports the heater whose OFF throws`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "true")
        val h1 = givenRegisteredHeater()
        val h2 = givenRegisteredHeater()
        h2.givenOffThrows()

        // When
        val result = sut.execute(false)

        // Then
        withClue("a heater whose driver throws on OFF should be reported in failedHeaters") {
            result.shouldBeRight() shouldBe SetHeatingEnabledReport(failedHeaters = setOf(h2.uuid))
        }
        withClue("an exception from one driver should not stop the OFF of the other heaters") {
            h1.commands shouldBe listOf(HeaterCommandSent.OFF)
        }
    }

    @Test fun `execute() sends OFF again to every heater, also to one already off, when heating is disabled again`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "false")
        val h1 = givenRegisteredHeater(status = ActuatorStatus.OFF)
        val h2 = givenRegisteredHeater(status = ActuatorStatus.ON)

        // When
        sut.execute(false)
        sut.execute(false)

        // Then
        withClue("each disable should send OFF to every heater, whatever its status, so the caller can retry") {
            h1.commands shouldBe listOf(HeaterCommandSent.OFF, HeaterCommandSent.OFF)
            h2.commands shouldBe listOf(HeaterCommandSent.OFF, HeaterCommandSent.OFF)
        }
    }

    @Test fun `execute() keeps the flag saved and returns DevicesUnavailable when the devices cannot be read`() {
        // Given
        propertyRepository.givenProperty(HEATING_ENABLED_KEY, "true")
        val heater = givenRegisteredHeater()
        every { devicesRepository.getAll() } returns DeviceRepositoryError.left()

        // When
        val result = sut.execute(false)

        // Then
        withClue("the result should say that the heaters were not turned off") {
            result.shouldBeLeft() shouldBe DevicesUnavailable
        }
        withClue("heating.enabled should stay saved as \"false\"") {
            propertyRepository.storedValue(HEATING_ENABLED_KEY) shouldBe "false"
        }
        withClue("connected clients should receive the saved flag") {
            verify(exactly = 1) { homeStateRefreshPublisher.publish() }
        }
        withClue("no heater should receive OFF when the registered heaters are unknown") {
            heater.commands shouldBe emptyList()
        }
    }

    private fun givenRegisteredHeater(status: ActuatorStatus = ActuatorStatus.ON): FakeHeater {
        val heater = FakeHeater(status = status)
        val record = anActuator(uuid = heater.uuid)
        registeredDevices.add(record)
        every { factory.createDevice(record) } returns heater
        return heater
    }
}
