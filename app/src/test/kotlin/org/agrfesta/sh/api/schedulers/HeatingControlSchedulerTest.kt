package org.agrfesta.sh.api.schedulers

import arrow.core.left
import arrow.core.right
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.agrfesta.sh.api.core.application.ports.inbounds.heating.EvaluateHeatingStateUseCase
import org.agrfesta.sh.api.core.domain.failures.ActuatorOperationFailure
import org.agrfesta.sh.api.core.domain.failures.HeatingFlagUnavailable
import org.agrfesta.sh.api.core.domain.heating.ActuationOutcome
import org.agrfesta.sh.api.core.domain.heating.HeaterActionOutcome
import org.agrfesta.sh.api.core.domain.heating.HeaterCommand
import org.agrfesta.sh.api.core.domain.heating.HeatingEvaluationReport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.UUID

class HeatingControlSchedulerTest {

    private val evaluateHeatingState: EvaluateHeatingStateUseCase = mockk()
    private val sut = HeatingControlScheduler(evaluateHeatingState)

    private val logger = LoggerFactory.getLogger(HeatingControlScheduler::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>().apply { start() }

    init {
        logger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        logger.detachAppender(appender)
    }

    @Test
    fun `scheduledTask() logs an error for each Failed outcome in the report`() {
        // Given — two heaters: one actuated fine, one failed
        val failure = object : ActuatorOperationFailure {}
        val failedHeaterId = UUID.randomUUID()
        every { evaluateHeatingState.execute() } returns HeatingEvaluationReport.Evaluated(
            listOf(
                HeaterActionOutcome(UUID.randomUUID(), HeaterCommand.ON, ActuationOutcome.Issued),
                HeaterActionOutcome(failedHeaterId, HeaterCommand.OFF, ActuationOutcome.Failed(failure))
            )
        ).right()

        // When
        sut.scheduledTask()

        // Then
        withClue("each failed actuation logs an error naming heater and cause; successes are quiet") {
            appender.list.count {
                it.level == Level.ERROR &&
                    it.formattedMessage.contains(failedHeaterId.toString()) &&
                    it.formattedMessage.contains(failure.toString())
            } shouldBe 1
            appender.list.count { it.level == Level.ERROR } shouldBe 1
        }
    }

    @Test
    fun `scheduledTask() is quiet when the evaluation succeeds without failed actuations`() {
        // Given
        every { evaluateHeatingState.execute() } returns HeatingEvaluationReport.Evaluated(
            listOf(HeaterActionOutcome(UUID.randomUUID(), HeaterCommand.ON, ActuationOutcome.Issued))
        ).right()

        // When
        sut.scheduledTask()

        // Then
        withClue("a successful evaluation with no failed actuation must not log errors") {
            appender.list.count { it.level == Level.ERROR } shouldBe 0
        }
    }

    @Test
    fun `scheduledTask() is quiet when the evaluation is skipped`() {
        // Given
        every { evaluateHeatingState.execute() } returns HeatingEvaluationReport.Skipped.right()

        // When
        sut.scheduledTask()

        // Then
        withClue("a skipped evaluation (heating disabled) must not log errors") {
            appender.list.count { it.level == Level.ERROR } shouldBe 0
        }
    }

    @Test
    fun `scheduledTask() logs the failure when evaluation returns Left`() {
        // Given
        every { evaluateHeatingState.execute() } returns HeatingFlagUnavailable.left()

        // When
        sut.scheduledTask()

        // Then
        withClue("a failed heating evaluation must be logged as an error by the scheduler") {
            appender.list.count {
                it.level == Level.ERROR && it.formattedMessage.contains("HeatingFlagUnavailable")
            } shouldBe 1
        }
    }
}
