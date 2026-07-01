package org.agrfesta.sh.api.core.application.usecases

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderError
import org.agrfesta.sh.api.core.domain.failures.RefreshDevicesError
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.aProviderDeviceData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class RefreshDevicesServiceTest {
    private val devicesRepository: DevicesRepository = mockk()
    private val randomGenerator: RandomGenerator = mockk()
    private val provider: DevicesProvider = mockk()

    private val sut = RefreshDevicesService(devicesRepository, setOf(provider), randomGenerator)

    @BeforeEach
    fun setUp() {
        every { devicesRepository.getAll() } returns emptyList<Device>().right()
        every { provider.getAllDevices() } returns emptyList<ProviderDeviceData>().right()
        every { devicesRepository.update(any()) } returns Unit.right()
        every { devicesRepository.create(any(), any(), any()) } returns Unit.right()
        every { randomGenerator.uuid() } returns UUID.randomUUID()
    }

    @Test
    fun `execute() returns RefreshDevicesError when DevicesRepository getAll() fails`() {
        // Given
        every { devicesRepository.getAll() } returns DeviceRepositoryError.left()

        // When
        val result = sut.execute()

        // Then
        result.shouldBeLeft().shouldBeInstanceOf<RefreshDevicesError>()
    }

    @Test
    fun `execute() returns empty result when no stored devices and provider returns empty list`() {
        // Given
        every { devicesRepository.getAll() } returns emptyList<Device>().right()
        every { provider.getAllDevices() } returns emptyList<ProviderDeviceData>().right()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        result.newDevices.shouldBeEmpty()
        result.updatedDevices.shouldBeEmpty()
        result.detachedDevices.shouldBeEmpty()
    }

    @Test
    fun `execute() returns empty result when no stored devices and provider fails`() {
        // Given
        every { provider.getAllDevices() } returns DevicesProviderError(RuntimeException("provider unavailable")).left()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        result.newDevices.shouldBeEmpty()
        result.updatedDevices.shouldBeEmpty()
        result.detachedDevices.shouldBeEmpty()
    }

    @Test
    fun `execute() returns new devices when provider returns a device not yet in the DB`() {
        // Given
        val newDeviceData = aProviderDeviceData()
        val generatedUuid = UUID.randomUUID()
        every { provider.getAllDevices() } returns listOf(newDeviceData).right()
        every { randomGenerator.uuid() } returns generatedUuid
        every { devicesRepository.create(generatedUuid, newDeviceData, any()) } returns Unit.right()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        withClue("newDevices should contain the persisted Device with the generated UUID") {
            result.newDevices.shouldContainExactly(Device(uuid = generatedUuid, providerData = newDeviceData))
        }
        result.updatedDevices.shouldBeEmpty()
        result.detachedDevices.shouldBeEmpty()
    }

    @Test
    fun `execute() returns updated devices when provider returns a device already persisted`() {
        // Given
        val providerDeviceData = aProviderDeviceData()
        val storedDevice = aDevice(
            providerId = providerDeviceData.deviceProviderId,
            provider = providerDeviceData.provider,
            status = DeviceStatus.PAIRED
        )
        every { devicesRepository.getAll() } returns listOf(storedDevice).right()
        every { provider.getAllDevices() } returns listOf(providerDeviceData).right()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        result.newDevices.shouldBeEmpty()
        withClue("updatedDevices should contain the stored device with refreshed name, PAIRED status and model") {
            result.updatedDevices.shouldContainExactly(
                storedDevice.copy(
                    name = providerDeviceData.name,
                    status = DeviceStatus.PAIRED,
                    model = providerDeviceData.model
                )
            )
        }
        result.detachedDevices.shouldBeEmpty()
    }

    @Test
    fun `execute() a new device carries the provider model`() {
        // Given
        val newDeviceData = aProviderDeviceData()
        val generatedUuid = UUID.randomUUID()
        every { provider.getAllDevices() } returns listOf(newDeviceData).right()
        every { randomGenerator.uuid() } returns generatedUuid
        every { devicesRepository.create(generatedUuid, newDeviceData, any()) } returns Unit.right()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        withClue("a new device must carry the provider's model") {
            result.newDevices.single().model shouldBe newDeviceData.model
        }
    }

    @Test
    fun `execute() repopulates the model of an updated device from the provider data`() {
        // Given
        val providerDeviceData = aProviderDeviceData()
        val storedDevice = aDevice(
            providerId = providerDeviceData.deviceProviderId,
            provider = providerDeviceData.provider,
            status = DeviceStatus.PAIRED
        )
        every { devicesRepository.getAll() } returns listOf(storedDevice).right()
        every { provider.getAllDevices() } returns listOf(providerDeviceData).right()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        withClue("an updated device must carry the provider's model so a re-sync repopulates it") {
            result.updatedDevices.single().model shouldBe providerDeviceData.model
        }
    }

    @Test
    fun `execute() returns detached devices when a stored device is absent from all providers`() {
        // Given
        val storedDevice = aDevice(status = DeviceStatus.PAIRED)
        every { devicesRepository.getAll() } returns listOf(storedDevice).right()
        every { provider.getAllDevices() } returns emptyList<ProviderDeviceData>().right()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        result.newDevices.shouldBeEmpty()
        result.updatedDevices.shouldBeEmpty()
        withClue("detachedDevices should contain the stored device marked as DETACHED") {
            result.detachedDevices.shouldContainExactly(storedDevice.copy(status = DeviceStatus.DETACHED))
        }
    }

    @Test
    fun `execute() re-pairs a previously DETACHED device when a provider returns it again`() {
        // Given
        val providerDeviceData = aProviderDeviceData()
        val detachedDevice = aDevice(
            providerId = providerDeviceData.deviceProviderId,
            provider = providerDeviceData.provider,
            status = DeviceStatus.DETACHED
        )
        every { devicesRepository.getAll() } returns listOf(detachedDevice).right()
        every { provider.getAllDevices() } returns listOf(providerDeviceData).right()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        result.newDevices.shouldBeEmpty()
        withClue("detached device should be re-paired with status PAIRED") {
            result.updatedDevices.shouldContainExactly(
                detachedDevice.copy(
                    name = providerDeviceData.name,
                    status = DeviceStatus.PAIRED,
                    model = providerDeviceData.model
                )
            )
        }
        result.detachedDevices.shouldBeEmpty()
    }

    @Test
    fun `execute() correctly distributes devices across new, updated and detached with two providers`() {
        // Given
        val newDeviceData = aProviderDeviceData()
        val generatedUuid = UUID.randomUUID()
        val existingDeviceData = aProviderDeviceData()
        val storedMatchingDevice = aDevice(
            providerId = existingDeviceData.deviceProviderId,
            provider = existingDeviceData.provider,
            status = DeviceStatus.PAIRED
        )
        val storedOrphanDevice = aDevice(status = DeviceStatus.PAIRED)
        val providerA: DevicesProvider = mockk()
        val providerB: DevicesProvider = mockk()
        every { devicesRepository.getAll() } returns listOf(storedMatchingDevice, storedOrphanDevice).right()
        every { providerA.getAllDevices() } returns listOf(newDeviceData).right()
        every { providerB.getAllDevices() } returns listOf(existingDeviceData).right()
        every { randomGenerator.uuid() } returns generatedUuid
        every { devicesRepository.create(generatedUuid, newDeviceData, any()) } returns Unit.right()
        val sut = RefreshDevicesService(devicesRepository, setOf(providerA, providerB), randomGenerator)

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        withClue("newDevices") {
            result.newDevices.shouldContainExactly(Device(uuid = generatedUuid, providerData = newDeviceData))
        }
        withClue("updatedDevices") {
            result.updatedDevices.shouldContainExactly(
                storedMatchingDevice.copy(
                    name = existingDeviceData.name,
                    status = DeviceStatus.PAIRED,
                    model = existingDeviceData.model
                )
            )
        }
        withClue("detachedDevices") {
            result.detachedDevices.shouldContainExactly(storedOrphanDevice.copy(status = DeviceStatus.DETACHED))
        }
    }

    @Test
    fun `execute() omits a new device from the result when create() fails`() {
        // Given
        val newDeviceData = aProviderDeviceData()
        every { provider.getAllDevices() } returns listOf(newDeviceData).right()
        every { devicesRepository.create(any(), newDeviceData, any()) } returns DeviceRepositoryError.left()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        result.newDevices.shouldBeEmpty()
        result.updatedDevices.shouldBeEmpty()
        result.detachedDevices.shouldBeEmpty()
    }

    @Test
    fun `execute() continues persisting remaining new devices when one create() fails`() {
        // Given
        val failingDeviceData = aProviderDeviceData()
        val successDeviceData = aProviderDeviceData()
        val successUuid = UUID.randomUUID()
        every { provider.getAllDevices() } returns listOf(failingDeviceData, successDeviceData).right()
        every { randomGenerator.uuid() } returnsMany listOf(UUID.randomUUID(), successUuid)
        every { devicesRepository.create(any(), failingDeviceData, any()) } returns DeviceRepositoryError.left()
        every { devicesRepository.create(successUuid, successDeviceData, any()) } returns Unit.right()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        withClue("only the successfully persisted device should be in newDevices") {
            result.newDevices.shouldContainExactly(Device(uuid = successUuid, providerData = successDeviceData))
        }
    }

    @Test
    fun `execute() skips silently when update() fails for a detached device`() {
        // Given
        val storedDevice = aDevice(status = DeviceStatus.PAIRED)
        val expectedDetachedDevice = storedDevice.copy(status = DeviceStatus.DETACHED)
        every { devicesRepository.getAll() } returns listOf(storedDevice).right()
        every { provider.getAllDevices() } returns emptyList<ProviderDeviceData>().right()
        every { devicesRepository.update(expectedDetachedDevice) } returns DeviceRepositoryError.left()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        verify { devicesRepository.update(expectedDetachedDevice) }
        withClue("detachedDevices should still contain the device even when update fails") {
            result.detachedDevices.shouldContainExactly(expectedDetachedDevice)
        }
        result.newDevices.shouldBeEmpty()
        result.updatedDevices.shouldBeEmpty()
    }

    @Test
    fun `execute() treats a provider device with the same providerId but a different provider as a new device`() {
        // Given
        val netatmoDeviceData = aProviderDeviceData(provider = Provider.NETATMO)
        val storedSwitchbotDevice = aDevice(
            providerId = netatmoDeviceData.deviceProviderId,
            provider = Provider.SWITCHBOT
        )
        val generatedUuid = UUID.randomUUID()
        every { devicesRepository.getAll() } returns listOf(storedSwitchbotDevice).right()
        every { provider.getAllDevices() } returns listOf(netatmoDeviceData).right()
        every { randomGenerator.uuid() } returns generatedUuid
        every { devicesRepository.create(generatedUuid, netatmoDeviceData, any()) } returns Unit.right()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        withClue("NETATMO device with same providerId as an existing SWITCHBOT device should be treated as new") {
            result.newDevices.shouldContainExactly(Device(uuid = generatedUuid, providerData = netatmoDeviceData))
        }
    }

    @Test
    fun `execute() does not update a stored device when a different provider returns the same providerId`() {
        // Given
        val netatmoDeviceData = aProviderDeviceData(provider = Provider.NETATMO)
        val storedSwitchbotDevice = aDevice(
            providerId = netatmoDeviceData.deviceProviderId,
            provider = Provider.SWITCHBOT
        )
        every { devicesRepository.getAll() } returns listOf(storedSwitchbotDevice).right()
        every { provider.getAllDevices() } returns listOf(netatmoDeviceData).right()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        withClue("SWITCHBOT device must not appear in updatedDevices when only NETATMO returned the same providerId") {
            result.updatedDevices.shouldBeEmpty()
        }
    }

    @Test
    fun `execute() detaches a device when its provider doesn't return it even if another has the same providerId`() {
        // Given
        val sharedProviderId = aProviderDeviceData().deviceProviderId
        val storedSwitchbotDevice = aDevice(providerId = sharedProviderId, provider = Provider.SWITCHBOT)
        val netatmoDeviceData = aProviderDeviceData(providerId = sharedProviderId, provider = Provider.NETATMO)
        every { devicesRepository.getAll() } returns listOf(storedSwitchbotDevice).right()
        every { provider.getAllDevices() } returns listOf(netatmoDeviceData).right()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        withClue("SWITCHBOT device must appear in detachedDevices when SWITCHBOT did not return it") {
            result.detachedDevices.shouldContainExactly(storedSwitchbotDevice.copy(status = DeviceStatus.DETACHED))
        }
    }

    @Test
    fun `execute() skips silently when update() fails for an updated device`() {
        // Given
        val providerDeviceData = aProviderDeviceData()
        val storedDevice = aDevice(
            providerId = providerDeviceData.deviceProviderId,
            provider = providerDeviceData.provider,
            status = DeviceStatus.PAIRED
        )
        val expectedUpdatedDevice = storedDevice.copy(
            name = providerDeviceData.name,
            status = DeviceStatus.PAIRED,
            model = providerDeviceData.model
        )
        every { devicesRepository.getAll() } returns listOf(storedDevice).right()
        every { provider.getAllDevices() } returns listOf(providerDeviceData).right()
        every { devicesRepository.update(expectedUpdatedDevice) } returns DeviceRepositoryError.left()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        verify { devicesRepository.update(expectedUpdatedDevice) }
        withClue("updatedDevices should still contain the device even when update fails") {
            result.updatedDevices.shouldContainExactly(expectedUpdatedDevice)
        }
        result.newDevices.shouldBeEmpty()
        result.detachedDevices.shouldBeEmpty()
    }
}
