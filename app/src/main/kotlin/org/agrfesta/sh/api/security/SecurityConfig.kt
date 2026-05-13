package org.agrfesta.sh.api.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter

@Configuration
class SecurityConfig(private val simpleApiKeyFilter: SimpleApiKeyFilter) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() } // stateless API
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .addFilterBefore(simpleApiKeyFilter, BasicAuthenticationFilter::class.java)
        return http.build()
    }
}
