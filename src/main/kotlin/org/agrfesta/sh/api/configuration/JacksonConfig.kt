package org.agrfesta.sh.api.configuration

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import org.agrfesta.sh.api.domain.commons.Temperature
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {
    
    @Bean
    fun temperatureModule(): SimpleModule {
        val module = SimpleModule()
        module.addSerializer(Temperature::class.java, TemperatureSerializer())
        module.addDeserializer(Temperature::class.java, TemperatureDeserializer())
        return module
    }
}

class TemperatureSerializer : JsonSerializer<Temperature>() {
    override fun serialize(value: Temperature, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeNumber(value.value)
    }
}

class TemperatureDeserializer : JsonDeserializer<Temperature>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Temperature {
        return Temperature(p.decimalValue.stripTrailingZeros())
    }
}
