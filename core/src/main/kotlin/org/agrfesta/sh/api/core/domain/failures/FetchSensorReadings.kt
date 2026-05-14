package org.agrfesta.sh.api.core.domain.failures

sealed interface FetchSensorReadingsFailure
data class FetchSensorReadingsError(val exception: Exception) : FetchSensorReadingsFailure
