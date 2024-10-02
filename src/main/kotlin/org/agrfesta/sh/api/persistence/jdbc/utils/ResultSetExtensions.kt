package org.agrfesta.sh.api.persistence.jdbc.utils

import org.agrfesta.sh.api.domain.devices.DeviceStatus
import org.agrfesta.sh.api.domain.devices.Provider
import java.sql.ResultSet

fun ResultSet.getProvider(columnLabel: String): Provider = Provider.valueOf(getString(columnLabel))

fun ResultSet.getStatus(columnLabel: String): DeviceStatus = DeviceStatus.valueOf(getString(columnLabel))
