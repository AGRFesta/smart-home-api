package org.agrfesta.sh.api

import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(CleanSmartHomeDatabaseExtension::class)
annotation class CleanSmartHomeDatabase
