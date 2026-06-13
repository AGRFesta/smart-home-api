package org.agrfesta.sh.api

import arrow.core.getOrElse
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.agrfesta.sh.api.controllers.AssignmentResponse
import org.agrfesta.sh.api.controllers.DeviceAggregateResponse
import org.agrfesta.sh.api.controllers.DeviceResponse
import org.agrfesta.sh.api.controllers.DevicesRefreshResponse
import org.agrfesta.sh.api.controllers.authenticated
import org.agrfesta.sh.api.controllers.toDevice
import org.agrfesta.sh.api.controllers.toResponse
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.SensorsAssignmentsRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.devices.AssignmentRole
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider.NETATMO
import org.agrfesta.sh.api.core.domain.devices.Provider.SWITCHBOT
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.aProviderDeviceData
import org.agrfesta.sh.api.domain.aSensorProviderData
import org.agrfesta.sh.api.domain.anAreaDto
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.providers.netatmo.NetatmoIntegrationAsserter
import org.agrfesta.sh.api.providers.switchbot.aSwitchBotDevice
import org.agrfesta.sh.api.providers.switchbot.aSwitchBotDevicesListSuccessResponse
import org.agrfesta.sh.api.providers.switchbot.toASwitchBotDeviceType
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class DevicesIntegrationTest(
    private val devicesDao: DevicesRepository,
    private val devicesRepository: DevicesJdbcRepository,
    private val areasRepository: AreasRepository,
    private val sensorsAssignmentsRepository: SensorsAssignmentsRepository,
    private val objectMapper: ObjectMapper,
    private val netatmoIntegrationAsserter: NetatmoIntegrationAsserter
) : AbstractIntegrationTest() {
    private val now = Instant.now()

    @BeforeEach
    fun init() {
        netatmoIntegrationAsserter.clear()
        devicesRepository.deleteAll()

        every { timeProvider.now() } returns now
    }

    @Test
    fun `synchronize() happy path with new, updated, re-paired and detached devices`() {
        val existingSBDeviceData = aProviderDeviceData(provider = SWITCHBOT)
        val existingDetachedSBDeviceData = aProviderDeviceData(provider = SWITCHBOT)
        val orphanSBDeviceData = aProviderDeviceData(provider = SWITCHBOT)
        val newSBDeviceData = aProviderDeviceData(provider = SWITCHBOT)
        val existingUuid = UUID.randomUUID()
        devicesDao.create(existingUuid, existingSBDeviceData).getOrElse { error("Failed to create device: $it") }
        val expectedUpdatedDevice = aDevice(existingSBDeviceData, existingUuid)
        val detachedUuid = UUID.randomUUID()
        devicesDao.create(detachedUuid, existingDetachedSBDeviceData, DeviceStatus.DETACHED)
            .getOrElse { error("Failed to create device: $it") }
        val expectedRePairedDevice = aDevice(existingDetachedSBDeviceData, detachedUuid, DeviceStatus.PAIRED)
        val orphanUuid = UUID.randomUUID()
        devicesDao.create(orphanUuid, orphanSBDeviceData).getOrElse { error("Failed to create device: $it") }
        val expectedDetachedDevice = aDevice(orphanSBDeviceData, orphanUuid, DeviceStatus.DETACHED)
        coEvery {
            switchBotDevicesClient.getDevices()
        } returns objectMapper.aSwitchBotDevicesListSuccessResponse(
            listOf(
                newSBDeviceData.asSBDeviceJsonNode(),
                existingSBDeviceData.asSBDeviceJsonNode(),
                existingDetachedSBDeviceData.asSBDeviceJsonNode()
            )
        )
        netatmoIntegrationAsserter.givenNoDevices()

        val result = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .post("/devices/synchronizations")
            .then()
            .statusCode(200)
            .extract()
            .`as`(DevicesRefreshResponse::class.java)

        result.newDevices.shouldHaveSize(1)
        val newDevice = result.newDevices.first()
        newDevice.deviceProviderId shouldBe newSBDeviceData.deviceProviderId
        newDevice.provider shouldBe newSBDeviceData.provider
        newDevice.name shouldBe newSBDeviceData.name
        newDevice.features shouldBe newSBDeviceData.features
        result.updatedDevices.shouldContainExactlyInAnyOrder(
            expectedUpdatedDevice.toResponse(),
            expectedRePairedDevice.toResponse()
        )
        result.detachedDevices.shouldContainExactly(expectedDetachedDevice.toResponse())
        devicesDao.getAll().getOrElse { error("Failed to fetch devices: $it") }
            .shouldContainExactlyInAnyOrder(
                expectedUpdatedDevice,
                expectedRePairedDevice,
                expectedDetachedDevice,
                newDevice.toDevice()
            )
    }

    @Test
    fun `GET devices returns persisted devices filtered by provider`() {
        val switchbotId = UUID.randomUUID()
        val switchbotData = aProviderDeviceData(provider = SWITCHBOT)
        devicesDao.create(switchbotId, switchbotData).getOrElse { error("Failed to create device: $it") }
        devicesDao.create(UUID.randomUUID(), aProviderDeviceData(provider = NETATMO))
            .getOrElse { error("Failed to create device: $it") }

        val response = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .queryParam("provider", "SWITCHBOT")
            .`when`()
            .get("/devices")
            .then()
            .statusCode(200)
            .extract()
            .`as`(Array<DeviceResponse>::class.java)

        response.toList().shouldContainExactly(aDevice(switchbotData, switchbotId).toResponse())
    }

    @Test
    fun `GET device by id returns the persisted aggregate with its current sensor assignment`() {
        val deviceId = UUID.randomUUID()
        val sensorData = aSensorProviderData()
        devicesDao.create(deviceId, sensorData).getOrElse { error("Failed to create device: $it") }
        val area = anAreaDto(name = aRandomUniqueString())
        areasRepository.save(area).getOrElse { error("Failed to save area: $it") }
        sensorsAssignmentsRepository.assign(areaId = area.uuid, sensorId = deviceId)
            .getOrElse { error("Failed to assign sensor: $it") }

        val responseBody = given()
            .contentType(ContentType.JSON)
            .authenticated()
            .`when`()
            .get("/devices/{uuid}", deviceId)
            .then()
            .statusCode(200)
            .extract()
            .asString()

        val response = objectMapper.readValue(responseBody, DeviceAggregateResponse::class.java)
        response.uuid shouldBe deviceId
        response.deviceProviderId shouldBe sensorData.deviceProviderId
        response.provider shouldBe sensorData.provider
        response.name shouldBe sensorData.name
        response.features shouldBe setOf(SENSOR)
        response.status shouldBe DeviceStatus.PAIRED
        response.createdOn shouldBe now.truncatedTo(ChronoUnit.SECONDS)
        response.updatedOn shouldBe null
        response.assignments.shouldContainExactly(
            AssignmentResponse(areaUuid = area.uuid, areaName = area.name, role = AssignmentRole.SENSOR)
        )
    }

    private fun ProviderDeviceData.asSBDeviceJsonNode(): JsonNode =
        objectMapper.aSwitchBotDevice(
            deviceId = deviceProviderId,
            deviceName = name,
            deviceType = features.toASwitchBotDeviceType()
        )
}
