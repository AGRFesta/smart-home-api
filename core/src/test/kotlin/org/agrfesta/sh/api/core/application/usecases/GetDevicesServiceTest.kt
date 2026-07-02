package org.agrfesta.sh.api.core.application.usecases

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.devices.DeviceModelCatalog
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.domain.aDevicePrototype
import org.junit.jupiter.api.Test

class GetDevicesServiceTest {
    private val devicesRepository: DevicesRepository = mockk()

    private val sensorModel = DeviceModel("test/sensor")
    private val actuatorModel = DeviceModel("test/actuator")
    private val catalog = DeviceModelCatalog(
        listOf(
            aDevicePrototype(model = sensorModel, roles = setOf(SENSOR)),
            aDevicePrototype(model = actuatorModel, roles = setOf(ACTUATOR))
        )
    )

    private val sut = GetDevicesService(devicesRepository, catalog)

    @Test
    fun `execute() with a feature filter keeps only devices whose model has that role`() {
        // Given
        val provider = Provider.SWITCHBOT
        val status = DeviceStatus.PAIRED
        val sensor = aDevice(name = "sensor-device", model = sensorModel)
        val actuator = aDevice(name = "actuator-device", model = actuatorModel)
        // The repository returns a non-matching device too: filtering by derived role is the service's job.
        every { devicesRepository.getDevices(provider, status) } returns listOf(sensor, actuator).right()

        // When
        val result = sut.execute(provider, status, SENSOR).shouldBeRight()

        // Then
        withClue("feature=SENSOR must keep only devices whose model resolves to the SENSOR role via the catalog") {
            result.shouldContainExactly(sensor)
        }
    }

    @Test
    fun `execute() without a feature filter returns every device the repository returns`() {
        // Given
        val provider = Provider.SWITCHBOT
        val status = DeviceStatus.PAIRED
        val sensor = aDevice(name = "sensor-device", model = sensorModel)
        val actuator = aDevice(name = "actuator-device", model = actuatorModel)
        every { devicesRepository.getDevices(provider, status) } returns listOf(sensor, actuator).right()

        // When
        val result = sut.execute(provider, status, feature = null).shouldBeRight()

        // Then
        withClue("a null feature filter must not drop any device") {
            result.shouldContainExactly(sensor, actuator)
        }
    }
}
