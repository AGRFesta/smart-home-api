package org.agrfesta.sh.api.persistence.jdbc.utils

import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import java.sql.ResultSet

fun ResultSet.getProvider(columnLabel: String): Provider = Provider.valueOf(getString(columnLabel))

fun ResultSet.getStatus(columnLabel: String): DeviceStatus = DeviceStatus.valueOf(getString(columnLabel))

fun ResultSet.getFeatures(columnLabel: String): Set<DeviceFeature> =
    (getArray(columnLabel).array as Array<*>).map { DeviceFeature.valueOf(it as String) }.toSet()
