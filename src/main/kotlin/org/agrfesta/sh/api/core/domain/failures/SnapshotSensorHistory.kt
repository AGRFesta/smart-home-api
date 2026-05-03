package org.agrfesta.sh.api.core.domain.failures

sealed interface SnapshotSensorHistoryFailure
data class SnapshotSensorHistoryError(override val exception: Exception) : SnapshotSensorHistoryFailure, ExceptionFailure
