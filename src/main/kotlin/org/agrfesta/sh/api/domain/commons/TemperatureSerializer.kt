package org.agrfesta.sh.api.domain.commons

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.math.BigDecimal

class TemperatureSerializer : JsonSerializer<Temperature>() {
    override fun serialize(value: Temperature, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeNumber(value.value)
    }
}

class TemperatureDeserializer : JsonDeserializer<Temperature>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Temperature {
        val decimal = p.decimalValue
        return Temperature(decimal.stripTrailingZeros())
    }
}
