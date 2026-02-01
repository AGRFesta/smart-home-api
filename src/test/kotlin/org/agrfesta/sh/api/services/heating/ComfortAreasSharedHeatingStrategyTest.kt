package org.agrfesta.sh.api.services.heating

import arrow.core.nonEmptySetOf
import arrow.core.right
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.areas.HeatableArea
import org.agrfesta.sh.api.domain.commons.SharedHeaterContext
import org.agrfesta.sh.api.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.domain.devices.SharedHeater
import org.junit.jupiter.api.Test

class ComfortAreasSharedHeatingStrategyTest {
    private val hysteresis = 0.5.toBigDecimal()
    private val config = HeatingConfiguration(hysteresis)
    private val sharedHeater: SharedHeater = mockk(relaxed = true) {
        every { uuid } returns UUID.randomUUID()
    }
    private val areaC: HeatableArea = mockk {
        every { uuid } returns UUID.randomUUID()
        every { heater } returns sharedHeater
    }
    private val areaCContext = HeatingAreaContext(hysteresis, areaC)
    private val areaA: HeatableArea = mockk {
        every { uuid } returns UUID.randomUUID()
        every { heater } returns sharedHeater
    }
    private val areaAContext = HeatingAreaContext(hysteresis, areaA)
    private val areaB: HeatableArea = mockk {
        every { uuid } returns UUID.randomUUID()
        every { heater } returns sharedHeater
    }
    private val areaBContext = HeatingAreaContext(hysteresis, areaB)
    private val areas = nonEmptySetOf(areaB, areaC, areaA)

    private val heatingService = HeatingService(config)
    private val sut = ComfortAreasSharedHeatingStrategyService(heatingService)

    @Test
    fun `handleHeatingFor() Turn the heater on when an area needs heating`() {
        coEvery { sharedHeater.getActuatorStatus() } returns ActuatorStatus.OFF.right()
        areaAContext.hasTempAsTarget()
        areaBContext.hasTempBelowTargetRange()
        areaC.hasNoTargetTemp() // in this case we consider heating not needed
        val context = SharedHeaterContext(sharedHeater, areas)

        runBlocking { sut.handleHeatingFor(context) }

        coVerify(exactly = 1) { sharedHeater.on() }
        coVerify(exactly = 0) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Keep the heater on when an area still needs heating`() {
        coEvery { sharedHeater.getActuatorStatus() } returns ActuatorStatus.ON.right()
        areaAContext.hasTempAboveTargetRange()
        areaBContext.hasTempAsTarget()
        areaCContext.hasTempAboveTargetRange()
        val context = SharedHeaterContext(sharedHeater, areas)

        runBlocking { sut.handleHeatingFor(context) }

        coVerify(exactly = 1) { sharedHeater.on() }
        coVerify(exactly = 0) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Turn the heater off when all areas are above target range`() {
        coEvery { sharedHeater.getActuatorStatus() } returns ActuatorStatus.ON.right()
        areaAContext.hasTempAboveTargetRange()
        areaBContext.hasTempAboveTargetRange()
        areaCContext.hasTempAboveTargetRange()
        val context = SharedHeaterContext(sharedHeater, areas)

        runBlocking { sut.handleHeatingFor(context) }

        coVerify(exactly = 0) { sharedHeater.on() }
        coVerify(exactly = 1) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Keep the heater off when areas are in range`() {
        coEvery { sharedHeater.getActuatorStatus() } returns ActuatorStatus.OFF.right()
        areaAContext.hasTempInTargetRangeAboveTarget()
        areaBContext.hasTempInTargetRangeBelowTarget()
        areaCContext.hasTempInTargetRangeBelowTarget()
        val context = SharedHeaterContext(sharedHeater, areas)

        runBlocking { sut.handleHeatingFor(context) }

        coVerify(exactly = 0) { sharedHeater.on() }
        coVerify(exactly = 1) { sharedHeater.off() }
    }

    @Test
    fun `handleHeatingFor() Turn the heater off when is unable to fetch current areas temperature`() {
        coEvery { sharedHeater.getActuatorStatus() } returns ActuatorStatus.ON.right()
        areaA.hasUnavailableCurrentTemp()
        areaB.hasUnavailableCurrentTemp()
        areaC.hasUnavailableCurrentTemp()
        val context = SharedHeaterContext(sharedHeater, areas)

        runBlocking { sut.handleHeatingFor(context) }

        coVerify(exactly = 0) { sharedHeater.on() }
        coVerify(exactly = 1) { sharedHeater.off() }
    }

}
