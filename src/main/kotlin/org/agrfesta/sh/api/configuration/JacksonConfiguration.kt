package org.agrfesta.sh.api.configuration

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
import org.agrfesta.sh.api.domain.commons.Temperature
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

val domainModule = SimpleModule().apply {
    addDeserializer(Temperature::class.java, TemperatureDeserializer())
    addSerializer(Temperature::class.java, TemperatureSerializer())
}

val SMART_HOME_OBJECT_MAPPER: ObjectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .registerModule(domainModule)
    .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

@Configuration
class JacksonConfiguration {

    @Bean
    fun objectMapper(): ObjectMapper = SMART_HOME_OBJECT_MAPPER

}

class TemperatureDeserializer : StdDeserializer<Temperature>(Temperature::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Temperature {
        val numericValue: BigDecimal = p.decimalValue
        return Temperature.of(numericValue)
    }
}

class TemperatureSerializer : StdSerializer<Temperature>(Temperature::class.java) {
    override fun serialize(value: Temperature, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeNumber(value.value)
    }
}
