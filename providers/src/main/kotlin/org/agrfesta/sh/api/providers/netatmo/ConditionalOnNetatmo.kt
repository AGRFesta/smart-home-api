package org.agrfesta.sh.api.providers.netatmo

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(prefix = "providers.netatmo", name = ["enabled"], havingValue = "true")
annotation class ConditionalOnNetatmo
