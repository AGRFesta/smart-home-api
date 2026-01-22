package org.agrfesta.sh.api.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class TimeConfiguration {

    @Bean
    fun clock(@Value("\${smart-home.timezone:#{null}}") timezone: String?): Clock {
        return Clock.system(if (timezone.isNullOrBlank()) ZoneId.systemDefault() else ZoneId.of(timezone))
    }

}
