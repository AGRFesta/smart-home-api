package org.agrfesta.sh.api.persistence.jdbc.adapters

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import java.time.Instant
import java.util.*
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.aProviderDeviceData
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.test.mothers.aProvider
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException

class DevicesJdbcAdapterTest : AbstractJdbcAdapterTest() {

    @Autowired private lateinit var sut: DevicesJdbcAdapter

    @Test
    fun `getDeviceById() Returns DeviceNotFound when area is missing`() {
        every { timeService.now() } returns Instant.now()
        val missingDeviceId = UUID.randomUUID()

        sut.getDeviceById(missingDeviceId)
            .shouldBeLeft()
            .shouldBeInstanceOf<DeviceNotFound>()
            .missingDeviceId shouldBe missingDeviceId
    }

    @Test
    fun `getDeviceById() Returns device`() {
        every { timeService.now() } returns Instant.now()
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
    fun `getDeviceById() Returns PersistenceFailure when fails to fetch device`() {
        every { timeService.now() } returns Instant.now()
        val deviceId = UUID.randomUUID()
        val failure = DataAccessResourceFailureException("device fetching failure")
        every { devicesRepo.findDeviceById(deviceId) } throws failure

        sut.getDeviceById(deviceId)
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    // getAll()

    @Test
    fun `getAll() Returns empty collection when no devices exist`() {
        every { timeService.now() } returns Instant.now()

        sut.getAll()
            .shouldBeRight()
            .shouldHaveSize(0)
    }

    @Test
    fun `getAll() Returns all persisted devices`() {
        every { timeService.now() } returns Instant.now()
        devicesRepo.persist(UUID.randomUUID(), aProviderDeviceData())
        devicesRepo.persist(UUID.randomUUID(), aProviderDeviceData())

        sut.getAll()
            .shouldBeRight()
            .shouldHaveSize(2)
    }

    @Test
    fun `getAll() Returns PersistenceFailure when fails to fetch devices`() {
        every { timeService.now() } returns Instant.now()
        val failure = DataAccessResourceFailureException("devices fetching failure")
        every { devicesRepo.getAll() } throws failure

        sut.getAll()
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    // create()

    @Test
    fun `create() Persists a device retrievable by the given UUID`() {
        every { timeService.now() } returns Instant.now()
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
    fun `create() Returns PersistenceFailure when fails to persist device`() {
        every { timeService.now() } returns Instant.now()
        val device = aProviderDeviceData()
        val failure = DataAccessResourceFailureException("device creation failure")
        every { devicesRepo.persist(any(), any(), any()) } throws failure

        sut.create(UUID.randomUUID(), device)
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }

    // update()

    @Test
    fun `update() Updates device data`() {
        every { timeService.now() } returns Instant.now()
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
        every { timeService.now() } returns Instant.now()
        val missingDevice = aDevice()

        sut.update(missingDevice)
            .shouldBeLeft()
            .shouldBeInstanceOf<DeviceNotFound>()
            .missingDeviceId shouldBe missingDevice.uuid
    }

    @Test
    fun `update() Returns PersistenceFailure when fails to update device`() {
        every { timeService.now() } returns Instant.now()
        val data = aProviderDeviceData()
        val deviceId = UUID.randomUUID()
        devicesRepo.persist(deviceId, data)
        val device = aDevice(data = data, uuid = deviceId)
        val failure = DataAccessResourceFailureException("device update failure")
        every { devicesRepo.update(any()) } throws failure

        sut.update(device)
            .shouldBeLeft()
            .shouldBeInstanceOf<PersistenceFailure>()
    }
}