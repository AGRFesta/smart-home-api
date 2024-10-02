package org.agrfesta.sh.api.schedulers

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.agrfesta.sh.api.persistence.DevicesDao
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.providers.switchbot.SwitchBotService
import org.agrfesta.sh.api.utils.Cache
import org.junit.jupiter.api.Test

class DevicesDataFetchSchedulerUnitTest {
    private val cache: Cache = mockk()
    private val devicesDao: DevicesDao = mockk()
    private val switchBotDevicesClient: SwitchBotDevicesClient = mockk()

    private val mapper = ObjectMapper()
    private val switchBotService = SwitchBotService(devicesClient = switchBotDevicesClient, mapper = mapper)
    private val sut = DevicesDataFetchScheduler(
        devicesRepository = devicesDao,
        switchBotService = switchBotService,
        cache = cache
    )

    @Test fun `fetchDevicesData() do not cache any value when there are no devices`() {
        every { devicesDao.getAll() } returns emptyList()

        sut.fetchDevicesData()

        verify(exactly = 0) { cache.set(any(), any()) }
    }

}
