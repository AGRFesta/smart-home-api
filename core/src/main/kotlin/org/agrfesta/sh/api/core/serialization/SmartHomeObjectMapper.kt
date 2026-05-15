package org.agrfesta.sh.api.core.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant

/**
 * Custom serializer for [Instant] that writes timestamps as epoch seconds.
 *
 * **Design Decision:** This serializer intentionally truncates sub-second precision
 * (milliseconds/nanoseconds) to maintain API consistency with external integrations.
 *
 * **Note:** This is a deliberate architectural choice, not an oversight. Changing
 * this to milliseconds would break existing API contracts and external integrations.
 * Any future changes to timestamp precision should be done via API versioning.
 */
internal class InstantSerializer : StdSerializer<Instant>(Instant::class.java) {
    override fun serialize(value: Instant, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeNumber(value.epochSecond)
    }
}

val SMART_HOME_OBJECT_MAPPER: ObjectMapper = jacksonObjectMapper().apply {
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
    configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
    configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true)
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    registerModule(JavaTimeModule())
    registerModule(SimpleModule().apply {
        addSerializer(Instant::class.java, InstantSerializer())
    })
}
