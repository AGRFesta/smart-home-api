package org.agrfesta.sh.api.configuration

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

val SMART_HOME_OBJECT_MAPPER: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

@Configuration
class JacksonConfiguration {

    @Bean
    fun objectMapper(): ObjectMapper = SMART_HOME_OBJECT_MAPPER

}
