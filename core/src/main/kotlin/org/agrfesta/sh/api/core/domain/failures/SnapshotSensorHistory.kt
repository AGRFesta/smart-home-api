package org.agrfesta.sh.api.core.domain.failures

sealed interface SnapshotSensorHistoryFailure
data object SnapshotSensorHistoryError : SnapshotSensorHistoryFailure
