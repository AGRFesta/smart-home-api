package org.agrfesta.sh.api.security

import jakarta.servlet.DispatcherType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter

/** Actuator endpoints reachable without an API key (status only; details stay gated by `show-details`). */
internal val PUBLIC_HEALTH_ENDPOINTS = arrayOf(
    "/actuator/health",
    "/actuator/health/liveness",
    "/actuator/health/readiness"
)

@Configuration
class SecurityConfig(private val simpleApiKeyFilter: SimpleApiKeyFilter) {

    @Bean
    @Suppress("SpreadOperator") // vararg requestMatchers; one-off copy of a small static array at startup
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() } // stateless API
            .authorizeHttpRequests {
                // Container-internal ERROR dispatches to /error must render: the API-key filter
                // skips them (OncePerRequestFilter default) and blocking them would mask every
                // unhandled 500 as an entry-point 403. Direct client requests to /error are
                // REQUEST dispatches and stay authenticated.
                it.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers(*PUBLIC_HEALTH_ENDPOINTS).permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(simpleApiKeyFilter, BasicAuthenticationFilter::class.java)
        return http.build()
    }
}
