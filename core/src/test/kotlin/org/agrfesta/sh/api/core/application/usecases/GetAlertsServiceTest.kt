package org.agrfesta.sh.api.core.application.usecases

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.outbounds.alerts.AlertsRepository
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.domain.anAlert
import org.agrfesta.test.annotations.PropertyBasedTest
import org.agrfesta.test.property.pbtConfig
import org.junit.jupiter.api.Test

class GetAlertsServiceTest {
    private val alertsRepository: AlertsRepository = mockk()

    private val sut = GetAlertsService(alertsRepository)

    @Test
    fun `execute() defaults to the OPEN alerts when no status is given`() {
        // Given
        val openAlerts = listOf(anAlert(), anAlert())
        every { alertsRepository.getAlerts(AlertStatus.OPEN) } returns openAlerts.right()

        // When
        val result = sut.execute().shouldBeRight()

        // Then
        result.shouldContainExactly(openAlerts)
        verify { alertsRepository.getAlerts(AlertStatus.OPEN) }
    }

    @PropertyBasedTest
    @Test
    fun `execute() forwards any given status to the repository`() {
        runBlocking {
            checkAll(pbtConfig, Arb.enum<AlertStatus>()) { status ->
                // Given
                every { alertsRepository.getAlerts(status) } returns listOf(anAlert()).right()

                // When
                sut.execute(status)

                // Then
                verify { alertsRepository.getAlerts(status) }
            }
        }
    }
}
