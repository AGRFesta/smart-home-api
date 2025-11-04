package org.agrfesta.sh.api.providers.netatmo

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import org.agrfesta.test.mothers.aRandomBoolean
import org.agrfesta.test.mothers.aRandomHour
import org.agrfesta.test.mothers.aRandomIntPercentage
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
    humidity: Int = aRandomIntPercentage(),
    openWindow: Boolean = aRandomBoolean(),
    measuredTemperature: Float = aRandomTemperature().toFloat(),
    setpointTemperature: Float = aRandomTemperature().toFloat(),
    setpointMode: String = "home",
    setpointStartTime: Instant? = null,
    setpointEndTime: Instant? = null
) = NetatmoRoomStatus(id, humidity, openWindow, measuredTemperature, setpointTemperature, setpointMode,
    setpointStartTime, setpointEndTime)

fun aNetatmoHomeStatus(
    id: String = aRandomUniqueString(),
    rooms: Collection<NetatmoRoomStatus> = listOf(aNetatmoRoomStatus())
) = NetatmoHomeStatus(id, rooms)
