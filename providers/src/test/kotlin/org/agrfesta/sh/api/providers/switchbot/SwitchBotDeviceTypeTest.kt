package org.agrfesta.sh.api.providers.switchbot

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SwitchBotDeviceTypeTest {

    @Test
    fun `model exposes the provider-qualified model name for each device type`() {
        SwitchBotDeviceType.METER.model shouldBe "switchbot/Meter"
        SwitchBotDeviceType.METER_PLUS.model shouldBe "switchbot/MeterPlus"
        SwitchBotDeviceType.WO_IO_SENSOR.model shouldBe "switchbot/WoIOSensor"
        SwitchBotDeviceType.HUB_MINI.model shouldBe "switchbot/Hub Mini"
    }
}
