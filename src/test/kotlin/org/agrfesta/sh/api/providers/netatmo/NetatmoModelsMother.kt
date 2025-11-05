package org.agrfesta.sh.api.providers.netatmo

import java.time.Instant
import org.agrfesta.sh.api.domain.commons.RelativeHumidityHundreds
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.test.mothers.aRandomBoolean
import org.agrfesta.test.mothers.aRandomHumidity
import org.agrfesta.test.mothers.aRandomTemperature
import org.agrfesta.test.mothers.aRandomUniqueString

fun aNetatmoModule(
    id: String = aRandomUniqueString(),
    name: String = aRandomUniqueString(),
    type: String = "BNS",
    roomId: String = aRandomUniqueString()
) = NetatmoModuleData(id, type, name, roomId)


fun aNetatmoRoomStatus(
    id: String = aRandomUniqueString(),
    humidity: RelativeHumidityHundreds = aRandomHumidity().toHundreds(),
    openWindow: Boolean = aRandomBoolean(),
    measuredTemperature: Temperature = aRandomTemperature(),
    setpointTemperature: Temperature = aRandomTemperature(),
    setpointMode: String = "home",
    setpointStartTime: Instant? = null,
    setpointEndTime: Instant? = null
) = NetatmoRoomStatus(id, humidity, openWindow, measuredTemperature, setpointTemperature, setpointMode,
    setpointStartTime, setpointEndTime)

fun aNetatmoHomeStatus(
    id: String = aRandomUniqueString(),
    rooms: Collection<NetatmoRoomStatus> = listOf(aNetatmoRoomStatus())
) = NetatmoHomeStatus(id, rooms)
