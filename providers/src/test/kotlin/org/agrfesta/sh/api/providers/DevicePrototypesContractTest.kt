package org.agrfesta.sh.api.providers

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.Actuator
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicePrototype
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.Sensor
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.ACTUATOR
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature.SENSOR
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.domain.aDevice
import org.agrfesta.sh.api.providers.hon.HonApplianceStore
import org.agrfesta.sh.api.providers.hon.devices.HonAcAs25Prototype
import org.agrfesta.sh.api.providers.hon.devices.HonAcAs35Prototype
import org.agrfesta.sh.api.providers.hon.devices.HonDevicesFactory
import org.agrfesta.sh.api.providers.netatmo.NetatmoClient
import org.agrfesta.sh.api.providers.netatmo.NetatmoConfiguration
import org.agrfesta.sh.api.providers.netatmo.devices.NetatmoDevicesFactory
import org.agrfesta.sh.api.providers.netatmo.devices.NetatmoSmartherPrototype
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDeviceType
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.providers.switchbot.devices.SwitchBotDevicesFactory
import org.agrfesta.sh.api.providers.switchbot.devices.SwitchBotHubPrototype
import org.agrfesta.sh.api.providers.switchbot.devices.SwitchBotMeterPlusPrototype
import org.agrfesta.sh.api.providers.switchbot.devices.SwitchBotMeterPrototype
import org.agrfesta.sh.api.providers.switchbot.devices.SwitchBotWoIOSensorPrototype
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * The prototype catalog is the single source of truth for `model → driver` and `model → roles`.
 * These contract tests pin the two residual couplings so a prototype can never silently disagree
 * with the factory it describes, nor with the capabilities its driver actually implements.
 */
class DevicePrototypesContractTest {

    private val netatmoConfig = NetatmoConfiguration(
        clientId = "id",
        clientSecret = "secret",
        baseUrl = "url",
        homeId = "home",
        roomId = "room"
    )

    private val factories: Map<Provider, ProviderDevicesFactory> = listOf(
        SwitchBotDevicesFactory(mockk<SwitchBotDevicesClient>()),
        NetatmoDevicesFactory(netatmoConfig, mockk<NetatmoClient>(), mockk<TimeProvider>()),
        HonDevicesFactory(HonApplianceStore(), mockk())
    ).associateBy { it.provider }

    private val prototypes: List<DevicePrototype> = listOf(
        SwitchBotMeterPrototype(),
        SwitchBotMeterPlusPrototype(),
        SwitchBotWoIOSensorPrototype(),
        SwitchBotHubPrototype(),
        NetatmoSmartherPrototype(),
        HonAcAs25Prototype(),
        HonAcAs35Prototype()
    )

    @TestFactory
    fun `the provider factory builds a driver assignable to each prototype's driverType`() =
        prototypes.map { prototype ->
            dynamicTest("${prototype.model.value} -> ${prototype.driverType.simpleName}") {
                val factory = factories.getValue(prototype.provider)
                val device = aDevice(provider = prototype.provider, model = prototype.model)

                val driver = factory.createDevice(device)

                withClue("driver ${driver::class.simpleName} is not a ${prototype.driverType.simpleName}") {
                    prototype.driverType.isInstance(driver) shouldBe true
                }
            }
        }

    @TestFactory
    fun `each prototype's roles match the capability interfaces its driver implements`() =
        prototypes.map { prototype ->
            dynamicTest(prototype.model.value) {
                val driverIsSensor = Sensor::class.java.isAssignableFrom(prototype.driverType.java)
                val driverIsActuator = Actuator::class.java.isAssignableFrom(prototype.driverType.java)

                withClue("SENSOR role must match Sensor capability for ${prototype.model.value}") {
                    (SENSOR in prototype.roles) shouldBe driverIsSensor
                }
                withClue("ACTUATOR role must match Actuator capability for ${prototype.model.value}") {
                    (ACTUATOR in prototype.roles) shouldBe driverIsActuator
                }
            }
        }

    @Test fun `every SwitchBotDeviceType has a registered prototype`() {
        val registered = prototypes.map { it.model.value }.toSet()

        SwitchBotDeviceType.entries.forEach { type ->
            withClue("no prototype registered for model '${type.model}'") {
                (type.model in registered) shouldBe true
            }
        }
    }
}
