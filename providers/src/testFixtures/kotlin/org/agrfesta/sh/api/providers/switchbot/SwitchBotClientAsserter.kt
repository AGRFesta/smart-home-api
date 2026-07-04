package org.agrfesta.sh.api.providers.switchbot

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.coEvery
import org.agrfesta.sh.api.core.domain.commons.ThermoHygroData
import org.agrfesta.sh.api.providers.switchbot.ConditionalOnSwitchBot
import org.springframework.stereotype.Service

@Service
@ConditionalOnSwitchBot
class SwitchBotClientAsserter(
    private val switchBotDevicesClient: SwitchBotDevicesClient,
    private val mapper: ObjectMapper
) {

    // battery defaults to a deterministic full charge: a random default would make any test that
    // triggers a polling cycle nondeterministically cross the BATTERY_LOW hysteresis band.
    fun givenSensorData(
        sensorProviderId: String,
        data: ThermoHygroData,
        battery: Int = 100
    ) {
        coEvery { switchBotDevicesClient.getDeviceStatus(sensorProviderId) } returns
                mapper.aSwitchBotDeviceStatusResponse(
                    humidity = data.relativeHumidity.value.movePointRight(2).toInt(),
                    temperature = data.temperature,
                    battery = battery
                )
    }

    fun givenSensorDataFailure(sensorProviderId: String, throwable: Throwable = Exception("sensor readings failure")) {
        coEvery { switchBotDevicesClient.getDeviceStatus(sensorProviderId) } throws throwable
    }

}
