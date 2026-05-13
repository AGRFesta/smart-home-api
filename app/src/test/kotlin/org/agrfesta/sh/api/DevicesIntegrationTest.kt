package org.agrfesta.sh.api

import arrow.core.getOrElse
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import java.time.Instant
import java.util.UUID
import org.agrfesta.sh.api.controllers.DevicesRefreshResponse
import org.agrfesta.sh.api.controllers.authenticated
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.aProviderDeviceData
import org.agrfesta.sh.api.controllers.toDevice
import org.agrfesta.sh.api.controllers.toResponse
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider.NETATMO
import org.agrfesta.sh.api.core.domain.devices.Provider.SWITCHBOT
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.persistence.jdbc.repositories.DevicesJdbcRepository
import org.agrfesta.sh.api.providers.netatmo.NetatmoIntegrationAsserter
import org.agrfesta.sh.api.providers.switchbot.aSwitchBotDevice
import org.agrfesta.sh.api.providers.switchbot.aSwitchBotDevicesListSuccessResponse
import org.agrfesta.sh.api.providers.switchbot.toASwitchBotDeviceType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DevicesIntegrationTest(
    private val devicesDao: DevicesRepository,
    private val devicesRepository: DevicesJdbcRepository,
    private val objectMapper: ObjectMapper,
    private val netatmoIntegrationAsserter: NetatmoIntegrationAsserter
): AbstractIntegrationTest() {
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
        } returns objectMapper.aSwitchBotDevicesListSuccessResponse(listOf(
            newSBDeviceData.asSBDeviceJsonNode(),
            existingSBDeviceData.asSBDeviceJsonNode(),
            existingDetachedSBDeviceData.asSBDeviceJsonNode()))
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
            expectedUpdatedDevice.toResponse(), expectedRePairedDevice.toResponse())
        result.detachedDevices.shouldContainExactly(expectedDetachedDevice.toResponse())
        devicesDao.getAll().getOrElse { error("Failed to fetch devices: $it") }
            .shouldContainExactlyInAnyOrder(
                expectedUpdatedDevice, expectedRePairedDevice, expectedDetachedDevice, newDevice.toDevice())
    }


    private fun ProviderDeviceData.asSBDeviceJsonNode(): JsonNode =
        objectMapper.aSwitchBotDevice(
            deviceId = deviceProviderId,
            deviceName = name,
            deviceType = features.toASwitchBotDeviceType()
        )

}
