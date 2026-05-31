package org.agrfesta.sh.api.home

import arrow.core.left
import arrow.core.right
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.agrfesta.sh.api.core.application.ports.inbounds.GetHomeDashboardUseCase
import org.agrfesta.sh.api.core.domain.commons.FieldSuccess
import org.agrfesta.sh.api.core.domain.failures.GetHomeDashboardFailure
import org.agrfesta.sh.api.core.domain.home.GlobalStateDto
import org.agrfesta.sh.api.core.domain.home.HomeDashboardDto
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

class HomeStreamBroadcasterTest {
    private val getHomeDashboardUseCase: GetHomeDashboardUseCase = mockk()
    private val sseEmitterFactory: SseEmitterFactory = mockk()
    private val emitter: SseEmitter = mockk(relaxed = true)

    private val sut = HomeStreamBroadcaster(getHomeDashboardUseCase, sseEmitterFactory)

    private val aDashboard = HomeDashboardDto(
        globalState = GlobalStateDto(heatingActive = FieldSuccess(false), strategy = FieldSuccess(null)),
        areas = emptyList()
    )

    // register() ///////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `register() sends the current home dashboard as the initial event`() {
        every { sseEmitterFactory.create() } returns emitter
        every { getHomeDashboardUseCase.execute() } returns aDashboard.right()

        sut.register()

        verify { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test fun `register() closes the emitter gracefully when the initial dashboard fetch fails`() {
        every { sseEmitterFactory.create() } returns emitter
        every { getHomeDashboardUseCase.execute() } returns mockk<GetHomeDashboardFailure>().left()

        sut.register()

        verify { emitter.complete() }
        verify(exactly = 0) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test fun `register() does not add the emitter to the registry when the initial dashboard fetch fails`() {
        every { sseEmitterFactory.create() } returns emitter
        every { getHomeDashboardUseCase.execute() } returns mockk<GetHomeDashboardFailure>().left()
        sut.register()
        every { getHomeDashboardUseCase.execute() } returns aDashboard.right()

        sut.onHomeStateRefresh(HomeStateRefreshEvent(this))

        verify(exactly = 0) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test fun `register() removes the emitter from the registry when the initial send throws`() {
        every { sseEmitterFactory.create() } returns emitter
        every { getHomeDashboardUseCase.execute() } returns aDashboard.right()
        every { emitter.send(any<SseEmitter.SseEventBuilder>()) } throws IOException("client gone")
        sut.register()
        clearMocks(emitter, answers = false)

        sut.onHomeStateRefresh(HomeStateRefreshEvent(this))

        verify(exactly = 0) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test fun `register() removes the emitter from the registry when the onCompletion callback fires`() {
        val onCompletionSlot = slot<Runnable>()
        every { emitter.onCompletion(capture(onCompletionSlot)) } just Runs
        every { sseEmitterFactory.create() } returns emitter
        every { getHomeDashboardUseCase.execute() } returns aDashboard.right()
        sut.register()
        clearMocks(emitter, answers = false)

        onCompletionSlot.captured.run()

        sut.onHomeStateRefresh(HomeStateRefreshEvent(this))
        verify(exactly = 0) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test fun `register() removes the emitter from the registry when the onTimeout callback fires`() {
        val onTimeoutSlot = slot<Runnable>()
        every { emitter.onTimeout(capture(onTimeoutSlot)) } just Runs
        every { sseEmitterFactory.create() } returns emitter
        every { getHomeDashboardUseCase.execute() } returns aDashboard.right()
        sut.register()
        clearMocks(emitter, answers = false)

        onTimeoutSlot.captured.run()

        sut.onHomeStateRefresh(HomeStateRefreshEvent(this))
        verify(exactly = 0) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    // onHomeStateRefresh() /////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `onHomeStateRefresh() broadcasts the dashboard to all registered emitters`() {
        val firstEmitter: SseEmitter = mockk(relaxed = true)
        val secondEmitter: SseEmitter = mockk(relaxed = true)
        every { sseEmitterFactory.create() } returnsMany listOf(firstEmitter, secondEmitter)
        every { getHomeDashboardUseCase.execute() } returns aDashboard.right()
        sut.register()
        sut.register()
        clearMocks(firstEmitter, secondEmitter, answers = false)

        sut.onHomeStateRefresh(HomeStateRefreshEvent(this))

        verify { firstEmitter.send(any<SseEmitter.SseEventBuilder>()) }
        verify { secondEmitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test fun `onHomeStateRefresh() does not send anything when dashboard retrieval fails`() {
        every { sseEmitterFactory.create() } returns emitter
        every { getHomeDashboardUseCase.execute() } returns aDashboard.right()
        sut.register()
        clearMocks(emitter, answers = false)
        every { getHomeDashboardUseCase.execute() } returns mockk<GetHomeDashboardFailure>().left()

        sut.onHomeStateRefresh(HomeStateRefreshEvent(this))

        verify(exactly = 0) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test fun `onHomeStateRefresh() keeps broadcasting to healthy emitters and drops the broken one`() {
        val brokenEmitter: SseEmitter = mockk(relaxed = true)
        val healthyEmitter: SseEmitter = mockk(relaxed = true)
        every { sseEmitterFactory.create() } returnsMany listOf(brokenEmitter, healthyEmitter)
        every { getHomeDashboardUseCase.execute() } returns aDashboard.right()
        sut.register()
        sut.register()
        clearMocks(brokenEmitter, healthyEmitter, answers = false)
        every { brokenEmitter.send(any<SseEmitter.SseEventBuilder>()) } throws IOException("client gone")

        sut.onHomeStateRefresh(HomeStateRefreshEvent(this))
        sut.onHomeStateRefresh(HomeStateRefreshEvent(this))

        verify(exactly = 1) { brokenEmitter.send(any<SseEmitter.SseEventBuilder>()) }
        verify(exactly = 2) { healthyEmitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    // keepAlive() //////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `keepAlive() sends a keep-alive comment to every registered emitter`() {
        every { sseEmitterFactory.create() } returns emitter
        every { getHomeDashboardUseCase.execute() } returns aDashboard.right()
        sut.register()
        clearMocks(emitter, answers = false)

        sut.keepAlive()

        verify(exactly = 1) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
    }

    @Test fun `keepAlive() drops emitters whose keep-alive send fails`() {
        val brokenEmitter: SseEmitter = mockk(relaxed = true)
        every { sseEmitterFactory.create() } returns brokenEmitter
        every { getHomeDashboardUseCase.execute() } returns aDashboard.right()
        sut.register()
        clearMocks(brokenEmitter, answers = false)
        every { brokenEmitter.send(any<SseEmitter.SseEventBuilder>()) } throws IOException("client gone")

        sut.keepAlive()
        sut.keepAlive()

        verify(exactly = 1) { brokenEmitter.send(any<SseEmitter.SseEventBuilder>()) }
    }
}
