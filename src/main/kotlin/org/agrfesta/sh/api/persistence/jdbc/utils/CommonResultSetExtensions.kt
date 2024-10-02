package org.agrfesta.sh.api.persistence.jdbc.utils

import java.sql.ResultSet
import java.time.Instant
import java.util.*

fun ResultSet.getUuid(columnLabel: String): UUID = UUID.fromString(getString(columnLabel))

fun ResultSet.getInstant(columnLabel: String): Instant = getTimestamp(columnLabel).toInstant()
fun ResultSet.findInstant(columnLabel: String): Instant? = getTimestamp(columnLabel)?.toInstant()
