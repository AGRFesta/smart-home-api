package org.agrfesta.sh.api.core.serialization

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class JacksonConfigurationTest {
    private val objectMapper = JacksonConfiguration().objectMapper()

    private data class Request(val enabled: Boolean)

    @Test fun `objectMapper() ignores a field the target type does not declare`() {
        val request: Request = objectMapper.readValue("""{ "enabled": true, "foo": 1 }""")

        withClue("an unknown request field should be ignored, never rejected (docs/api/API_INDEX.md)") {
            request shouldBe Request(enabled = true)
        }
    }
}
