package org.agrfesta.sh.api.core.application.usecases.heating

import arrow.core.right
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalTime
import java.util.UUID
import org.agrfesta.sh.api.core.domain.areas.HeatableArea
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.devices.SharedHeater
import org.junit.jupiter.api.Test

class ComfortAreasSharedHeatingStrategyTest {
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

    private val sut = ComfortAreasSharedHeatingStrategyService()


    @Test
    fun `handleHeatingFor() Do nothing when there are no areas`() {
        sut.handleHeatingFor(sharedHeater, emptyList(), LocalTime.now())

        verify(exactly = 0) { sharedHeater.on() }
        verify(exactly = 0) { sharedHeater.off() }
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

        sut.handleHeatingFor(sharedHeater, areas, LocalTime.now())

        verify(exactly = 0) { sharedHeater.on() }
        verify(exactly = 0) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Turn the heater on when an area needs heating`() {
        every { sharedHeater.getActuatorStatus() } returns ActuatorStatus.OFF.right()
        areaA.hasTempAsTarget()
        areaB.hasTempBelowTargetRange()
        areaC.hasNoTargetTemp() // in this case we consider heating not needed

        sut.handleHeatingFor(sharedHeater, areas, LocalTime.now())

        verify(exactly = 1) { sharedHeater.on() }
        verify(exactly = 0) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Keep the heater on when an area still needs heating`() {
        every { sharedHeater.getActuatorStatus() } returns ActuatorStatus.ON.right()
        areaA.hasTempAboveTargetRange()
        areaB.hasTempAsTarget()
        areaC.hasTempAboveTargetRange()

        sut.handleHeatingFor(sharedHeater, areas, LocalTime.now())

        verify(exactly = 1) { sharedHeater.on() }
        verify(exactly = 0) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Turn the heater off when all areas are above target range`() {
        every { sharedHeater.getActuatorStatus() } returns ActuatorStatus.ON.right()
        areaA.hasTempAboveTargetRange()
        areaB.hasTempAboveTargetRange()
        areaC.hasTempAboveTargetRange()

        sut.handleHeatingFor(sharedHeater, areas, LocalTime.now())

        verify(exactly = 0) { sharedHeater.on() }
        verify(exactly = 1) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Keep the heater off when areas are in range`() {
        every { sharedHeater.getActuatorStatus() } returns ActuatorStatus.OFF.right()
        areaA.hasTempInTargetRangeAboveTarget()
        areaB.hasTempInTargetRangeBelowTarget()
        areaC.hasTempInTargetRangeBelowTarget()

        sut.handleHeatingFor(sharedHeater, areas, LocalTime.now())

        verify(exactly = 0) { sharedHeater.on() }
        verify(exactly = 1) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Turn the heater off when is unable to fetch current areas temperature`() {
        every { sharedHeater.getActuatorStatus() } returns ActuatorStatus.ON.right()
        areaA.hasUnavailableCurrentTemp()
        areaB.hasUnavailableCurrentTemp()
        areaC.hasUnavailableCurrentTemp()

        sut.handleHeatingFor(sharedHeater, areas, LocalTime.now())

        verify(exactly = 0) { sharedHeater.on() }
        verify(exactly = 1) { sharedHeater.off() }
    }

}
