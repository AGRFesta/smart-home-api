package org.agrfesta.sh.api.persistence.jdbc.adapters

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus.DETACHED
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus.PAIRED
import org.agrfesta.sh.api.core.domain.devices.Provider.NETATMO
import org.agrfesta.sh.api.core.domain.devices.Provider.SWITCHBOT
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.aProviderDeviceData
import org.agrfesta.test.mothers.aProvider
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Instant
import java.util.*

class DevicesJdbcAdapterTest : AbstractJdbcAdapterTest() {

    @Autowired private lateinit var sut: DevicesJdbcAdapter

    @Test
    fun `getDeviceById() Returns DeviceNotFound when area is missing`() {
        every { timeProvider.now() } returns Instant.now()
        val missingDeviceId = UUID.randomUUID()

        sut.getDeviceById(missingDeviceId)
            .shouldBeLeft()
            .shouldBeInstanceOf<DeviceNotFound>()
            .missingDeviceId shouldBe missingDeviceId
    }

    @Test
    fun `getDeviceById() Returns device`() {
        every { timeProvider.now() } returns Instant.now()
        val device = aProviderDeviceData(
            providerId = aRandomUniqueString(),
            provider = aProvider(),
            name = aRandomUniqueString(),
            features = emptySet() // TODO various features
        )
        val deviceId = UUID.randomUUID()
        devicesRepo.persist(deviceId, device)

        sut.getDeviceById(deviceId)
            .shouldBeRight().also {
                it.deviceProviderId shouldBe device.deviceProviderId
                it.provider shouldBe device.provider
                it.name shouldBe device.name
                it.features shouldBe device.features
            }
    }

    @Test
    fun `getDeviceById() Returns DeviceRepositoryError when fails to fetch device`() {
        every { timeProvider.now() } returns Instant.now()
        val deviceId = UUID.randomUUID()
        val failure = DataAccessResourceFailureException("device fetching failure")
        every { devicesRepo.findDeviceById(deviceId) } throws failure

        sut.getDeviceById(deviceId)
            .shouldBeLeft()
            .shouldBe(DeviceRepositoryError)
    }

    // getAll()

    @Test
    fun `getAll() Returns empty collection when no devices exist`() {
        every { timeProvider.now() } returns Instant.now()

        sut.getAll()
            .shouldBeRight()
            .shouldHaveSize(0)
    }

    @Test
    fun `getAll() Returns all persisted devices`() {
        every { timeProvider.now() } returns Instant.now()
        devicesRepo.persist(UUID.randomUUID(), aProviderDeviceData())
        devicesRepo.persist(UUID.randomUUID(), aProviderDeviceData())

        sut.getAll()
            .shouldBeRight()
            .shouldHaveSize(2)
    }

    @Test
    fun `getAll() Returns DeviceRepositoryError when fails to fetch devices`() {
        every { timeProvider.now() } returns Instant.now()
        val failure = DataAccessResourceFailureException("devices fetching failure")
        every { devicesRepo.getAll() } throws failure

        sut.getAll()
            .shouldBeLeft()
            .shouldBe(DeviceRepositoryError)
    }

    // getDevices()

    @Test
    fun `getDevices() Returns all persisted devices when no filters provided`() {
        every { timeProvider.now() } returns Instant.now()
        devicesRepo.persist(UUID.randomUUID(), aProviderDeviceData())
        devicesRepo.persist(UUID.randomUUID(), aProviderDeviceData())

        sut.getDevices()
            .shouldBeRight()
            .shouldHaveSize(2)
    }

    @Test
    fun `getDevices() filters by provider`() {
        every { timeProvider.now() } returns Instant.now()
        devicesRepo.persist(UUID.randomUUID(), aProviderDeviceData(provider = SWITCHBOT))
        devicesRepo.persist(UUID.randomUUID(), aProviderDeviceData(provider = NETATMO))

        val result = sut.getDevices(provider = SWITCHBOT).shouldBeRight()

        withClue("only SWITCHBOT devices should be returned") {
            result.map { it.provider }.shouldContainExactly(SWITCHBOT)
        }
    }

    @Test
    fun `getDevices() filters by status`() {
        every { timeProvider.now() } returns Instant.now()
        devicesRepo.persist(UUID.randomUUID(), aProviderDeviceData(), PAIRED)
        devicesRepo.persist(UUID.randomUUID(), aProviderDeviceData(), DETACHED)

        val result = sut.getDevices(status = PAIRED).shouldBeRight()

        withClue("only PAIRED devices should be returned") {
            result.map { it.status }.shouldContainExactly(PAIRED)
        }
    }

    @Test
    fun `getDevices() filters by feature`() {
        every { timeProvider.now() } returns Instant.now()
        devicesRepo.persist(UUID.randomUUID(), aProviderDeviceData(features = setOf(SENSOR)))
        devicesRepo.persist(UUID.randomUUID(), aProviderDeviceData(features = setOf(ACTUATOR)))

        val result = sut.getDevices(feature = SENSOR).shouldBeRight()

        withClue("only devices exposing SENSOR should be returned") {
            result.map { it.features }.shouldContainExactly(listOf(setOf(SENSOR)))
        }
    }

    @Test
    fun `getDevices() combines provider, status and feature filters with AND semantics`() {
        every { timeProvider.now() } returns Instant.now()
        val matchingId = UUID.randomUUID()
        val matchingData = aProviderDeviceData(provider = SWITCHBOT, features = setOf(SENSOR))
        devicesRepo.persist(matchingId, matchingData, PAIRED)
        // decoys, each differing from the filter in exactly one dimension
        devicesRepo.persist(
            UUID.randomUUID(),
            aProviderDeviceData(provider = NETATMO, features = setOf(SENSOR)),
            PAIRED
        )
        devicesRepo.persist(
            UUID.randomUUID(),
            aProviderDeviceData(provider = SWITCHBOT, features = setOf(SENSOR)),
            DETACHED
        )
        devicesRepo.persist(
            UUID.randomUUID(),
            aProviderDeviceData(provider = SWITCHBOT, features = setOf(ACTUATOR)),
            PAIRED
        )

        val result = sut.getDevices(provider = SWITCHBOT, status = PAIRED, feature = SENSOR).shouldBeRight()

        withClue("only the device matching all three filters should be returned") {
            result.shouldContainExactly(aDevice(matchingData, matchingId, PAIRED))
        }
    }

    @Test
    fun `getDevices() Returns DeviceRepositoryError when fails to fetch devices`() {
        every { timeProvider.now() } returns Instant.now()
        val failure = DataAccessResourceFailureException("devices fetching failure")
        every { devicesRepo.findDevices(any(), any(), any()) } throws failure

        sut.getDevices()
            .shouldBeLeft()
            .shouldBe(DeviceRepositoryError)
    }

    // create()

    @Test
    fun `create() Persists a device retrievable by the given UUID`() {
        every { timeProvider.now() } returns Instant.now()
        val device = aProviderDeviceData()
        val deviceId = UUID.randomUUID()

        sut.create(deviceId, device).shouldBeRight()

        sut.getDeviceById(deviceId)
            .shouldBeRight().also {
                it.deviceProviderId shouldBe device.deviceProviderId
                it.provider shouldBe device.provider
                it.name shouldBe device.name
                it.features shouldBe device.features
            }
    }

    @Test
    fun `create() Returns DeviceRepositoryError when fails to persist device`() {
        every { timeProvider.now() } returns Instant.now()
        val device = aProviderDeviceData()
        val failure = DataAccessResourceFailureException("device creation failure")
        every { devicesRepo.persist(any(), any(), any()) } throws failure

        sut.create(UUID.randomUUID(), device)
            .shouldBeLeft()
            .shouldBe(DeviceRepositoryError)
    }

    // update()

    @Test
    fun `update() Updates device data`() {
        every { timeProvider.now() } returns Instant.now()
        val data = aProviderDeviceData()
        val deviceId = UUID.randomUUID()
        devicesRepo.persist(deviceId, data)
        val updatedDevice = aDevice(data = data, uuid = deviceId)

        sut.update(updatedDevice).shouldBeRight()

        sut.getDeviceById(deviceId)
            .shouldBeRight().also {
                it.name shouldBe updatedDevice.name
                it.status shouldBe updatedDevice.status
            }
    }

    @Test
    fun `update() Returns DeviceNotFound when device does not exist`() {
        every { timeProvider.now() } returns Instant.now()
        val missingDevice = aDevice()

        sut.update(missingDevice)
            .shouldBeLeft()
            .shouldBeInstanceOf<DeviceNotFound>()
            .missingDeviceId shouldBe missingDevice.uuid
    }

    @Test
    fun `update() Returns DeviceRepositoryError when fails to update device`() {
        every { timeProvider.now() } returns Instant.now()
        val data = aProviderDeviceData()
        val deviceId = UUID.randomUUID()
        devicesRepo.persist(deviceId, data)
        val device = aDevice(data = data, uuid = deviceId)
        val failure = DataAccessResourceFailureException("device update failure")
        every { devicesRepo.update(any()) } throws failure

        sut.update(device)
            .shouldBeLeft()
            .shouldBe(DeviceRepositoryError)
    }
}
