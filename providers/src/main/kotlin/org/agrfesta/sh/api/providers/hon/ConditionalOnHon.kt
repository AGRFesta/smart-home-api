package org.agrfesta.sh.api.providers.hon

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(prefix = "providers.hon", name = ["enabled"], havingValue = "true")
annotation class ConditionalOnHon
