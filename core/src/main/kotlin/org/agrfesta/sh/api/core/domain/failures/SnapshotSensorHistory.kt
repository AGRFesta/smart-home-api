package org.agrfesta.sh.api.core.domain.failures

sealed interface SnapshotSensorHistoryFailure
data class SnapshotSensorHistoryError(val exception: Exception) : SnapshotSensorHistoryFailure
