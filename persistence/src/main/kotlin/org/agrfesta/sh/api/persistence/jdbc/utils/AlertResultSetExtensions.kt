package org.agrfesta.sh.api.persistence.jdbc.utils

import org.agrfesta.sh.api.core.domain.alerts.AlertScope
import org.agrfesta.sh.api.core.domain.alerts.AlertStatus
import org.agrfesta.sh.api.core.domain.alerts.AlertType
import java.sql.ResultSet

fun ResultSet.getAlertType(columnLabel: String): AlertType = AlertType.valueOf(getString(columnLabel))

fun ResultSet.getAlertScope(columnLabel: String): AlertScope = AlertScope.valueOf(getString(columnLabel))

fun ResultSet.getAlertStatus(columnLabel: String): AlertStatus = AlertStatus.valueOf(getString(columnLabel))
