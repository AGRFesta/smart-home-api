package org.agrfesta.sh.api.providers.hon.devices

import org.agrfesta.sh.api.core.domain.devices.AcFanSpeed
import org.agrfesta.sh.api.core.domain.devices.AcMode
import org.agrfesta.sh.api.core.domain.devices.AcPowerCommand
import org.agrfesta.sh.api.core.domain.devices.AcSettingsUpdate

/*
 * Domain <-> hOn wire-code maps of the AC `settings` protocol. Protocol constants (ported
 * from addhOn's AC_MODE_MAP / AC_FAN_MAP): the catalog only carries the admitted codes per
 * firmware, never their semantics — the write path still validates every code against the
 * device's catalog.
 */

/** The hOn wire changes of a partial update: one entry per provided field. */
internal fun AcSettingsUpdate.honChanges(): Map<String, String> = buildMap {
    power?.let { put("onOffStatus", it.honCode()) }
    mode?.let { put("machMode", it.honCode()) }
    targetTemperature?.let { put("tempSel", it.value.toPlainString()) }
    fanSpeed?.let { put("windSpeed", it.honCode()) }
}

/** Domain mode -> hOn `machMode` code. */
internal fun AcMode.honCode(): String = when (this) {
    AcMode.AUTO -> "0"
    AcMode.COOL -> "1"
    AcMode.DRY -> "2"
    AcMode.HEAT -> "4"
    AcMode.FAN_ONLY -> "6"
}

/** hOn `machMode` code -> domain mode; null when the device reports a code we do not map. */
internal fun String.toAcMode(): AcMode? = when (this) {
    "0" -> AcMode.AUTO
    "1" -> AcMode.COOL
    "2" -> AcMode.DRY
    "4" -> AcMode.HEAT
    "6" -> AcMode.FAN_ONLY
    else -> null
}

/** Domain fan speed -> hOn `windSpeed` code. */
internal fun AcFanSpeed.honCode(): String = when (this) {
    AcFanSpeed.HIGH -> "1"
    AcFanSpeed.MEDIUM -> "2"
    AcFanSpeed.LOW -> "3"
    AcFanSpeed.AUTO -> "5"
}

/** hOn `windSpeed` code -> domain fan speed; null when the device reports a code we do not map. */
internal fun String.toAcFanSpeed(): AcFanSpeed? = when (this) {
    "1" -> AcFanSpeed.HIGH
    "2" -> AcFanSpeed.MEDIUM
    "3" -> AcFanSpeed.LOW
    "5" -> AcFanSpeed.AUTO
    else -> null
}

/** Power command -> hOn `onOffStatus` code. */
internal fun AcPowerCommand.honCode(): String = when (this) {
    AcPowerCommand.ON -> "1"
    AcPowerCommand.OFF -> "0"
}
