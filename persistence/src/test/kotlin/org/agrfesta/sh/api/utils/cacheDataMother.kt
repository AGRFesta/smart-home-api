package org.agrfesta.sh.api.utils

import org.agrfesta.test.mothers.aRandomPercentage
import org.agrfesta.test.mothers.aRandomTemperature

fun aThermoHygroCacheJson(
    temperature: String = aRandomTemperature().toString(),
    humidity: String = aRandomPercentage().value.toString()
): String = """{"t":"","h":""}"""
