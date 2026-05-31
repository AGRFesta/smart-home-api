package org.agrfesta.sh.api.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringJUnitConfig(SpringHomeStateRefreshPublisher::class)
@RecordApplicationEvents
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SpringHomeStateRefreshPublisherSliceTest(
    private val sut: SpringHomeStateRefreshPublisher
) {

    @Test fun `publish() emits a HomeStateRefreshEvent into the context`(events: ApplicationEvents) {
        sut.publish()

        events.stream(HomeStateRefreshEvent::class.java).count() shouldBe 1
    }
}
