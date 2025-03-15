package org.agrfesta.sh.api.domain

import java.time.LocalTime
import java.util.*
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.test.mothers.aDailyTime
import org.agrfesta.test.mothers.aRandomTemperature

fun aTemperatureInterval(
    temperature: Temperature = aRandomTemperature(),
    startTime: LocalTime = aDailyTime(),
    endTime: LocalTime = aDailyTime(),
) = TemperatureInterval(temperature, startTime, endTime)

fun anAreaTemperatureSetting(
    areaId: UUID = UUID.randomUUID(),
    defaultTemperature: Temperature = aRandomTemperature(),
    temperatureSchedule: Set<TemperatureInterval> = emptySet()
) = AreaTemperatureSetting(areaId, defaultTemperature, temperatureSchedule)
