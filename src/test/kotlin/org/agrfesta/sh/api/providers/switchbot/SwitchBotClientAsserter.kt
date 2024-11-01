package org.agrfesta.sh.api.providers.switchbot

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.coEvery
import org.agrfesta.sh.api.domain.commons.Temperature
import org.springframework.stereotype.Service

@Service
class SwitchBotClientAsserter(
    private val switchBotDevicesClient: SwitchBotDevicesClient,
    private val mapper: ObjectMapper
) {

    fun givenSensorData(sensorProviderId: String, temperature: Temperature, humidity: Int) {
        coEvery { switchBotDevicesClient.getDeviceStatus(sensorProviderId) } returns
                mapper.aSwitchBotDeviceStatusResponse(
                    humidity = humidity,
                    temperature = temperature
                )
    }

    fun givenSensorDataFailure(sensorProviderId: String, throwable: Throwable = Exception("sensor readings failure")) {
        coEvery { switchBotDevicesClient.getDeviceStatus(sensorProviderId) } throws throwable
    }

}
