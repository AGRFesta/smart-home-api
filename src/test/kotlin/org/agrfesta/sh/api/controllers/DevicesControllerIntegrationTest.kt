package org.agrfesta.sh.api.controllers

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.aDeviceDataValue
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.devices.DeviceDataValue
import org.agrfesta.sh.api.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.services.DevicesRefreshResult
import org.agrfesta.sh.api.domain.devices.Provider.NETATMO
import org.agrfesta.sh.api.domain.devices.Provider.SWITCHBOT
import org.agrfesta.sh.api.domain.failures.ProviderFailure
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.providers.netatmo.NetatmoClient
import org.agrfesta.sh.api.providers.netatmo.aHomeData
import org.agrfesta.sh.api.providers.netatmo.anEmptyHomeData
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDeviceType.HUB_MINI
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDeviceType.METER
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDeviceType.METER_PLUS
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDeviceType.WO_IO_SENSOR
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.providers.switchbot.aSwitchBotDevice
import org.agrfesta.sh.api.providers.switchbot.aSwitchBotDevicesListSuccessResponse
import org.agrfesta.sh.api.providers.switchbot.toASwitchBotDeviceType
import org.agrfesta.sh.api.utils.Cache
import org.agrfesta.sh.api.utils.TimeService
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container

class DevicesControllerIntegrationTest(
    @Autowired private val devicesDao: DevicesDao,
    @Autowired private val devicesRepository: DevicesJdbcRepository,
    @Autowired private val cache: Cache,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired @MockkBean private val switchBotDevicesClient: SwitchBotDevicesClient,
    @Autowired @MockkBean private val netatmoClient: NetatmoClient,
    @Autowired @MockkBean private val timeService: TimeService
): AbstractIntegrationTest() {
    private val now = Instant.now()

    companion object {
        @Container
        @ServiceConnection
        val postgres = createPostgresContainer()

        @Container
        @ServiceConnection
        val redis = createRedisContainer()
    }

    @BeforeEach
    fun init() {
        devicesRepository.deleteAll()

        every { timeService.now() } returns now
    }

    @Test
    fun `refresh() correctly save new device`() {
        val expectedSBDeviceData = givenASBDeviceData()

        val token = aRandomUniqueString()
        cache.set("provider.netatmo.access-token", token)
        val expectedNetatmoDeviceData = aDeviceDataValue(provider = NETATMO, features = setOf(SENSOR, ACTUATOR))
        coEvery {
            netatmoClient.getHomesData(token)
        } returns objectMapper.aHomeData(
            name = expectedNetatmoDeviceData.name,
            deviceId = expectedNetatmoDeviceData.providerId
        ).right()

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .post("/devices/refresh")
            .then()
            .statusCode(200)
            .extract()
            .`as`(DevicesRefreshResponse::class.java)

        result.newDevices.shouldHaveSize(2)
        result.updatedDevices.shouldBeEmpty()
        result.detachedDevices.shouldBeEmpty()
        val newDevices = result.newDevices.associateBy { it.provider }

        val sbNewDevice = newDevices[SWITCHBOT]
        sbNewDevice.shouldNotBeNull()
        sbNewDevice.asDataValue() shouldBe expectedSBDeviceData
        devicesRepository.findByProviderAndProviderId(expectedSBDeviceData.provider, expectedSBDeviceData.providerId)
            .shouldBeRight().apply {
                shouldNotBeNull()
                name shouldBe expectedSBDeviceData.name
                provider shouldBe SWITCHBOT
                providerId shouldBe expectedSBDeviceData.providerId
                createdOn.truncatedTo(ChronoUnit.SECONDS) shouldBe now.truncatedTo(ChronoUnit.SECONDS)
                updatedOn.shouldBeNull()
            }

        val netatmoNewDevice = newDevices[NETATMO]
        netatmoNewDevice.shouldNotBeNull()
        netatmoNewDevice.asDataValue() shouldBe expectedNetatmoDeviceData
        devicesRepository.findByProviderAndProviderId(expectedSBDeviceData.provider, expectedSBDeviceData.providerId)
            .shouldBeRight().apply {
                shouldNotBeNull()
                name shouldBe expectedSBDeviceData.name
                provider shouldBe SWITCHBOT
                providerId shouldBe expectedSBDeviceData.providerId
                createdOn.truncatedTo(ChronoUnit.SECONDS) shouldBe now.truncatedTo(ChronoUnit.SECONDS)
                updatedOn.shouldBeNull()
            }

        val allDevices = devicesDao.getAll()
        allDevices.shouldContainExactlyInAnyOrder(netatmoNewDevice, sbNewDevice)
    }

    @Test
    fun `refresh() correctly mapping device features from switchbot device type`() {
        val hubMiniProviderId = aRandomUniqueString()
        val hubMini = objectMapper.aSwitchBotDevice(deviceId = hubMiniProviderId, deviceType = HUB_MINI)
        val meterPlusProviderId = aRandomUniqueString()
        val meterPlus = objectMapper.aSwitchBotDevice(deviceId = meterPlusProviderId, deviceType = METER_PLUS)
        val woIoSensorProviderId = aRandomUniqueString()
        val woIoSensor = objectMapper.aSwitchBotDevice(deviceId = woIoSensorProviderId, deviceType = WO_IO_SENSOR)
        val meterProviderId = aRandomUniqueString()
        val meter = objectMapper.aSwitchBotDevice(deviceId = meterProviderId, deviceType = METER)
        coEvery {
            switchBotDevicesClient.getDevices()
        } returns objectMapper.aSwitchBotDevicesListSuccessResponse(listOf(hubMini, meterPlus, woIoSensor, meter))

        givenNoNetatmoDevices()

        given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .post("/devices/refresh")
            .then()
            .statusCode(200)

        devicesRepository.findByProviderAndProviderId(SWITCHBOT, hubMiniProviderId)
            .shouldBeRight().apply {
                shouldNotBeNull()
                features.shouldBeEmpty()
            }
        devicesRepository.findByProviderAndProviderId(SWITCHBOT, meterPlusProviderId)
            .shouldBeRight().apply {
                shouldNotBeNull()
                features.shouldContainExactly(SENSOR)
            }
        devicesRepository.findByProviderAndProviderId(SWITCHBOT, woIoSensorProviderId)
            .shouldBeRight().apply {
                shouldNotBeNull()
                features.shouldContainExactly(SENSOR)
            }
        devicesRepository.findByProviderAndProviderId(SWITCHBOT, meterProviderId)
            .shouldBeRight().apply {
                shouldNotBeNull()
                features.shouldContainExactly(SENSOR)
            }
    }

    @Test
    fun `refresh() correctly update existing device`() {
        val existingSBDeviceData = aDeviceDataValue(provider = SWITCHBOT)
        val uuid0 = devicesDao.create(existingSBDeviceData)
        val actualSBDeviceData = existingSBDeviceData.copy(name = aRandomUniqueString()) // name changed
        val expectedUpdatedSBDevice = aDevice(actualSBDeviceData, uuid0)
        coEvery {
            switchBotDevicesClient.getDevices()
        } returns objectMapper.aSwitchBotDevicesListSuccessResponse(listOf(
            actualSBDeviceData.asSBDeviceJsonNode()))

        val existingNetatmoDeviceData = aDeviceDataValue(provider = NETATMO, features = setOf(SENSOR, ACTUATOR))
        val uuid1 = devicesDao.create(existingNetatmoDeviceData)
        val actualNetatmoDeviceData = existingNetatmoDeviceData.copy(name = aRandomUniqueString()) // name changed
        val expectedUpdatedNetatmoDevice = aDevice(actualNetatmoDeviceData, uuid1)
        val token = aRandomUniqueString()
        cache.set("provider.netatmo.access-token", token)
        coEvery {
            netatmoClient.getHomesData(token)
        } returns objectMapper.aHomeData(
            name = expectedUpdatedNetatmoDevice.name,
            deviceId = expectedUpdatedNetatmoDevice.providerId
        ).right()

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .post("/devices/refresh")
            .then()
            .statusCode(200)
            .extract()
            .`as`(DevicesRefreshResult::class.java)

        result.newDevices.shouldBeEmpty()
        result.updatedDevices.shouldContainExactlyInAnyOrder(expectedUpdatedNetatmoDevice, expectedUpdatedSBDevice)
        result.detachedDevices.shouldBeEmpty()
        devicesDao.getAll().shouldContainExactlyInAnyOrder(expectedUpdatedNetatmoDevice, expectedUpdatedSBDevice)

        devicesRepository.findByProviderAndProviderId(
                provider = existingSBDeviceData.provider,
                providerId = existingSBDeviceData.providerId)
            .shouldBeRight().apply {
                shouldNotBeNull()
                name shouldBe actualSBDeviceData.name
                updatedOn?.truncatedTo(ChronoUnit.SECONDS) shouldBe now.truncatedTo(ChronoUnit.SECONDS) //TODO think about it
            }
    }

    @Test
    fun `refresh() Update existing devices to 'DETACHED' when fails to fetch them from provider`() {
        val deviceData = aDeviceDataValue(provider = SWITCHBOT)
        val uuid = devicesDao.create(deviceData)
        val expectedUpdatedSBDevice = aDevice(deviceData, uuid, DeviceStatus.DETACHED)
        coEvery { switchBotDevicesClient.getDevices() } throws Exception("switchbot fetch failure")

        val existingNetatmoDeviceData = aDeviceDataValue(provider = NETATMO, features = setOf(SENSOR, ACTUATOR))
        val uuid1 = devicesDao.create(existingNetatmoDeviceData)
        val expectedUpdatedNetatmoDevice = aDevice(existingNetatmoDeviceData, uuid1, DeviceStatus.DETACHED)
        val token = aRandomUniqueString()
        cache.set("provider.netatmo.access-token", token)
        coEvery { netatmoClient.getHomesData(token) } returns ProviderFailure(Exception("netatmo fetch failure")).left()

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .post("/devices/refresh")
            .then()
            .statusCode(200)
            .extract()
            .`as`(DevicesRefreshResult::class.java)

        result.newDevices.shouldBeEmpty()
        result.updatedDevices.shouldBeEmpty()
        result.detachedDevices.shouldContainExactlyInAnyOrder(expectedUpdatedNetatmoDevice, expectedUpdatedSBDevice)
        devicesDao.getAll().shouldContainExactlyInAnyOrder(expectedUpdatedNetatmoDevice, expectedUpdatedSBDevice)
    }

    @Test
    fun `refresh() happy case with all possible device status`() {
        val existingSBDeviceData = aDeviceDataValue(provider = SWITCHBOT)
        val existingDetachedSBDeviceData = aDeviceDataValue(provider = SWITCHBOT)
        val newSBDeviceData = aDeviceDataValue(provider = SWITCHBOT)
        val uuid = devicesDao.create(existingSBDeviceData)
        val expectedUpdatedSBDevice: Device = aDevice(existingSBDeviceData, uuid)
        val detachedUuid = devicesDao.create(existingDetachedSBDeviceData, DeviceStatus.DETACHED)
        val expectedPairedDevice = aDevice(existingDetachedSBDeviceData, detachedUuid, DeviceStatus.PAIRED)
        coEvery {
            switchBotDevicesClient.getDevices()
        } returns objectMapper.aSwitchBotDevicesListSuccessResponse(listOf(
            newSBDeviceData.asSBDeviceJsonNode(),
            existingSBDeviceData.asSBDeviceJsonNode(),
            existingDetachedSBDeviceData.asSBDeviceJsonNode()))

        givenNoNetatmoDevices() // Depends on what to test for Netatmo

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .post("/devices/refresh")
            .then()
            .statusCode(200)
            .extract()
            .`as`(DevicesRefreshResponse::class.java)

        result.newDevices.shouldHaveSize(1)
        val newDevice = result.newDevices.first()
        newDevice.asDataValue() shouldBe newSBDeviceData
        result.updatedDevices.shouldContainExactly(expectedUpdatedSBDevice, expectedPairedDevice)
        result.detachedDevices.shouldBeEmpty()
        devicesDao.getAll().shouldContainExactlyInAnyOrder(expectedUpdatedSBDevice, expectedPairedDevice, newDevice)
    }

    private fun DeviceDataValue.asSBDeviceJsonNode(): JsonNode =
        objectMapper.aSwitchBotDevice(
            deviceId = providerId,
            deviceName = name,
            deviceType = features.toASwitchBotDeviceType()
        )

    private fun givenASBDeviceData(): DeviceDataValue {
        val expectedSBDeviceData = aDeviceDataValue(provider = SWITCHBOT)
        coEvery {
            switchBotDevicesClient.getDevices()
        } returns objectMapper.aSwitchBotDevicesListSuccessResponse(listOf(expectedSBDeviceData.asSBDeviceJsonNode()))
        return expectedSBDeviceData
    }

    private fun givenNoNetatmoDevices() {
        val token = aRandomUniqueString()
        cache.set("provider.netatmo.access-token", token)
        coEvery {
            netatmoClient.getHomesData(token)
        } returns objectMapper.anEmptyHomeData().right()
    }

}
