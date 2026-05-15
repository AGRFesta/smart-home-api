package org.agrfesta.sh.api.core.domain.failures

sealed interface FetchSensorReadingsFailure
data object FetchSensorReadingsError : FetchSensorReadingsFailure
