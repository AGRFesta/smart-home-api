package org.agrfesta.sh.api.controllers

import com.ninjasquad.springmockk.MockkBean
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.domain.DeviceStatus
import org.agrfesta.sh.api.domain.DevicesRefreshResult
import org.agrfesta.sh.api.domain.Provider
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.repositories.DevicesRepository
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.utils.TimeService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class DevicesControllerIntegrationTest(
    @Autowired private val devicesDao: DevicesDao,
    @Autowired private val devicesRepository: DevicesRepository,
    @Autowired @MockkBean private val switchBotDevicesClient: SwitchBotDevicesClient,
    @Autowired @MockkBean private val timeService: TimeService
) {

    companion object {

        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

    }

    @LocalServerPort private val port: Int? = null

    private val now = Instant.now()

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"
        devicesRepository.deleteAll()

        every { timeService.now() } returns now
    }

    @Test
    fun `refresh() correctly save new device`() {
        val newSBDevice = aDevice(provider = Provider.SWITCHBOT)
        coEvery { switchBotDevicesClient.getDevices() } returns listOf(newSBDevice)

        val result = given()
            .contentType(ContentType.JSON)
            .`when`()
            .post("/devices/refresh")
            .then()
            .statusCode(200)
            .extract()
            .`as`(DevicesRefreshResult::class.java)

        result.newDevices.shouldContainExactly(newSBDevice)
        result.updatedDevices.shouldBeEmpty()
        result.detachedDevices.shouldBeEmpty()
        devicesDao.getAll().shouldContainExactlyInAnyOrder(newSBDevice)
        val newDeviceEntity =
            devicesRepository.findByProviderAndProviderId(newSBDevice.provider, newSBDevice.providerId)
        newDeviceEntity.shouldNotBeNull()
        newDeviceEntity.name shouldBe newSBDevice.name
        newDeviceEntity.provider shouldBe Provider.SWITCHBOT
        newDeviceEntity.providerId shouldBe newSBDevice.providerId
        newDeviceEntity.createdOn.truncatedTo(ChronoUnit.SECONDS) shouldBe now.truncatedTo(ChronoUnit.SECONDS)
        newDeviceEntity.updatedOn.shouldBeNull()
    }

    @Test
    fun `refresh() correctly update existing device`() {
        val existingSBDevice = aDevice(provider = Provider.SWITCHBOT)
        val device = aDevice(providerId = existingSBDevice.providerId, provider = Provider.SWITCHBOT)
        devicesDao.save(device)
        val expectedUpdatedSBDevice = device.copy(name = existingSBDevice.name)
        coEvery { switchBotDevicesClient.getDevices() } returns listOf(existingSBDevice)

        val result = given()
            .contentType(ContentType.JSON)
            .`when`()
            .post("/devices/refresh")
            .then()
            .statusCode(200)
            .extract()
            .`as`(DevicesRefreshResult::class.java)

        result.newDevices.shouldBeEmpty()
        result.updatedDevices.shouldContainExactly(expectedUpdatedSBDevice)
        result.detachedDevices.shouldBeEmpty()
        devicesDao.getAll().shouldContainExactlyInAnyOrder(expectedUpdatedSBDevice)
        val updatedDeviceEntity =
            devicesRepository.findByProviderAndProviderId(existingSBDevice.provider, existingSBDevice.providerId)
        updatedDeviceEntity.shouldNotBeNull()
        updatedDeviceEntity.updatedOn?.truncatedTo(ChronoUnit.SECONDS) shouldBe now.truncatedTo(ChronoUnit.SECONDS)
    }

    @Test
    fun `refresh() happy case with switchbot fetch failure`() {
        val device = aDevice(provider = Provider.SWITCHBOT)
        devicesDao.save(device)
        val expectedUpdatedSBDevice = device.copy(status = DeviceStatus.DETACHED)
        coEvery { switchBotDevicesClient.getDevices() } throws Exception("switchbot fetch failure")

        val result = given()
            .contentType(ContentType.JSON)
            .`when`()
            .post("/devices/refresh")
            .then()
            .statusCode(200)
            .extract()
            .`as`(DevicesRefreshResult::class.java)

        result.newDevices.shouldBeEmpty()
        result.updatedDevices.shouldBeEmpty()
        result.detachedDevices.shouldContainExactly(expectedUpdatedSBDevice)
        devicesDao.getAll().shouldContainExactlyInAnyOrder(expectedUpdatedSBDevice)
    }

    @Test
    fun `refresh() happy case with all possible device status`() {
        val existingSBDevice = aDevice(provider = Provider.SWITCHBOT)
        val existingDetachedSBDevice = aDevice(provider = Provider.SWITCHBOT)
        val newSBDevice = aDevice(provider = Provider.SWITCHBOT)
        val device = aDevice(providerId = existingSBDevice.providerId, provider = Provider.SWITCHBOT)
        devicesDao.save(device)
        val expectedUpdatedSBDevice = device.copy(name = existingSBDevice.name)
        val detachedDevice = aDevice(
            providerId = existingDetachedSBDevice.providerId,
            provider = Provider.SWITCHBOT,
            status = DeviceStatus.DETACHED)
        devicesDao.save(detachedDevice)
        val expectedPairedDevice = detachedDevice.copy(
            name = existingDetachedSBDevice.name,
            status = DeviceStatus.PAIRED)
        coEvery {
            switchBotDevicesClient.getDevices()
        } returns listOf(newSBDevice, existingSBDevice, existingDetachedSBDevice)

        val result = given()
            .contentType(ContentType.JSON)
            .`when`()
            .post("/devices/refresh")
            .then()
            .statusCode(200)
            .extract()
            .`as`(DevicesRefreshResult::class.java)

        result.newDevices.shouldContainExactly(newSBDevice)
        result.updatedDevices.shouldContainExactly(expectedUpdatedSBDevice, expectedPairedDevice)
        result.detachedDevices.shouldBeEmpty()
        devicesDao.getAll().shouldContainExactlyInAnyOrder(expectedUpdatedSBDevice, expectedPairedDevice, newSBDevice)
    }

}
