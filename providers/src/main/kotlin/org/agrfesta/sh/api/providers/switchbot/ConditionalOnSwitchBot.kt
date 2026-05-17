package org.agrfesta.sh.api.providers.switchbot

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(prefix = "providers.switchbot", name = ["enabled"], havingValue = "true")
annotation class ConditionalOnSwitchBot
