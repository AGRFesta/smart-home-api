package org.agrfesta.sh.api.infrastructure.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.agrfesta.sh.api.core.domain.areas.TemperatureInterval.Companion.INTERVAL_TIME_FORMAT
import org.agrfesta.sh.api.core.domain.commons.Temperature

internal val domainModule = SimpleModule().apply {
    addDeserializer(Temperature::class.java, TemperatureDeserializer())
    addSerializer(Temperature::class.java, TemperatureSerializer())
    addSerializer(Instant::class.java, InstantSerializer())
    addDeserializer(LocalTime::class.java, LocalTimeDeserializer())
    addSerializer(LocalTime::class.java, LocalTimeSerializer())
}

val SMART_HOME_OBJECT_MAPPER: ObjectMapper = jacksonObjectMapper().apply {
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
    configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
    configure(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN, true)
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    registerModule(JavaTimeModule())
    registerModule(domainModule)
}

internal class TemperatureDeserializer : StdDeserializer<Temperature>(Temperature::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Temperature {
        val numericValue: BigDecimal = p.decimalValue
        return Temperature.of(numericValue)
    }
}

internal class TemperatureSerializer : StdSerializer<Temperature>(Temperature::class.java) {
    override fun serialize(value: Temperature, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeNumber(value.value)
    }
}

/**
 * Custom serializer for [Instant] that writes timestamps as epoch seconds.
 *
 * **Design Decision:** This serializer intentionally truncates sub-second precision
 * (milliseconds/nanoseconds) to maintain API consistency with external integrations
 * (e.g., Netatmo API expects `epochSecond` values in `therm_setpoint_end_time`).
 *
 * **Note:** This is a deliberate architectural choice, not an oversight. Changing
 * this to milliseconds would break existing API contracts and external integrations.
 * Any future changes to timestamp precision should be done via API versioning.
 */
internal class InstantSerializer : StdSerializer<Instant>(Instant::class.java) {
    override fun serialize(value: Instant, gen: JsonGenerator, provider: SerializerProvider) {
        // Write as epoch seconds (whole number) - see KDoc for design rationale
        gen.writeNumber(value.epochSecond)
    }
}

private val localTimeFormatter = DateTimeFormatter.ofPattern(INTERVAL_TIME_FORMAT)

internal class LocalTimeSerializer : StdSerializer<LocalTime>(LocalTime::class.java) {
    override fun serialize(value: LocalTime, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeString(value.format(localTimeFormatter))
    }
}

internal class LocalTimeDeserializer : StdDeserializer<LocalTime>(LocalTime::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LocalTime {
        val value = p.text
            ?: throw ctxt.weirdStringException(null, LocalTime::class.java, "null value not allowed; expected format: $INTERVAL_TIME_FORMAT")
        return try {
            LocalTime.parse(value, localTimeFormatter)
        } catch (e: DateTimeParseException) {
            throw ctxt.weirdStringException(value, LocalTime::class.java, "expected format: $INTERVAL_TIME_FORMAT")
        }
    }
}
