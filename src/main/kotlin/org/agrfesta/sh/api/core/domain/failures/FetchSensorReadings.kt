package org.agrfesta.sh.api.core.domain.failures

sealed interface FetchSensorReadingsFailure
data class FetchSensorReadingsError(override val exception: Exception) : FetchSensorReadingsFailure, ExceptionFailure
