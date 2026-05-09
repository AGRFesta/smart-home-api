package org.agrfesta.sh.api.core.application.usecases.heating

import arrow.core.right
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.util.*
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.domain.areas.HeatableArea
import org.agrfesta.sh.api.core.domain.commons.Percentage
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.devices.SharedHeater
import org.junit.jupiter.api.Test

class EconomyAreasSharedHeatingStrategyTest {
    val percentage = Percentage(BigDecimal("0.5"))

    private val sharedHeater: SharedHeater = mockk(relaxed = true) {
        every { uuid } returns UUID.randomUUID()
    }
    private val areaC: HeatableArea = mockk {
        every { uuid } returns UUID.randomUUID()
        every { heater } returns sharedHeater
    }
    private val areaA: HeatableArea = mockk {
        every { uuid } returns UUID.randomUUID()
        every { heater } returns sharedHeater
    }
    private val areaB: HeatableArea = mockk {
        every { uuid } returns UUID.randomUUID()
        every { heater } returns sharedHeater
    }
    private val areas = listOf(areaB, areaC, areaA)

    private val sut = EconomyAreasSharedHeatingStrategyService(percentage.value)

    @Test
    fun `handleHeatingFor() Do nothing when there are no areas`() {
        runBlocking { sut.handleHeatingFor(sharedHeater, emptyList()) }

        coVerify(exactly = 0) { sharedHeater.on() }
        coVerify(exactly = 0) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Do nothing when areas are not sharing same heater`() {
        val anotherSharedHeater: SharedHeater = mockk(relaxed = true) {
            every { uuid } returns UUID.randomUUID()
        }
        val areaC: HeatableArea = mockk {
            every { uuid } returns UUID.randomUUID()
            every { heater } returns sharedHeater
        }
        val areaA: HeatableArea = mockk {
            every { uuid } returns UUID.randomUUID()
            every { heater } returns anotherSharedHeater
        }
        val areaB: HeatableArea = mockk {
            every { uuid } returns UUID.randomUUID()
            every { heater } returns sharedHeater
        }
        val areas = listOf(areaB, areaC, areaA)

        runBlocking { sut.handleHeatingFor(sharedHeater, areas) }

        coVerify(exactly = 0) { sharedHeater.on() }
        coVerify(exactly = 0) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Keep the heater off when only one area needs heating`() {
        coEvery { sharedHeater.getActuatorStatus() } returns ActuatorStatus.OFF.right()
        areaA.hasTempAsTarget()
        areaB.hasTempBelowTargetRange()
        areaC.hasNoTargetTemp() // in this case we consider heating not needed

        runBlocking { sut.handleHeatingFor(sharedHeater, areas) }

        coVerify(exactly = 0) { sharedHeater.on() }
        coVerify(exactly = 1) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Turn the heater on when enough areas need heating`() {
        coEvery { sharedHeater.getActuatorStatus() } returns ActuatorStatus.OFF.right()
        areaA.hasTempBelowTargetRange()
        areaB.hasTempBelowTargetRange()
        areaC.hasTempAsTarget()

        runBlocking { sut.handleHeatingFor(sharedHeater, areas) }

        coVerify(exactly = 1) { sharedHeater.on() }
        coVerify(exactly = 0) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Turn the heater off when an area is above target range`() {
        coEvery { sharedHeater.getActuatorStatus() } returns ActuatorStatus.ON.right()
        areaA.hasTempBelowTargetRange()
        areaB.hasTempAboveTargetRange()
        areaC.hasTempBelowTargetRange()

        runBlocking { sut.handleHeatingFor(sharedHeater, areas) }

        coVerify(exactly = 0) { sharedHeater.on() }
        coVerify(exactly = 1) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Turn the heater off when is unable to fetch current areas temperature`() {
        coEvery { sharedHeater.getActuatorStatus() } returns ActuatorStatus.ON.right()
        areaA.hasUnavailableCurrentTemp()
        areaB.hasUnavailableCurrentTemp()
        areaC.hasUnavailableCurrentTemp()

        runBlocking { sut.handleHeatingFor(sharedHeater, areas) }

        coVerify(exactly = 0) { sharedHeater.on() }
        coVerify(exactly = 1) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Keep the heater on when no area is above target range`() {
        coEvery { sharedHeater.getActuatorStatus() } returns ActuatorStatus.ON.right()
        areaA.hasTempInTargetRangeAboveTarget()
        areaB.hasTempInTargetRangeAboveTarget()
        areaC.hasTempInTargetRangeAboveTarget()

        runBlocking { sut.handleHeatingFor(sharedHeater, areas) }

        coVerify(exactly = 1) { sharedHeater.on() }
        coVerify(exactly = 0) { sharedHeater.off() }
    }


}
