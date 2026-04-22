package org.agrfesta.sh.api.core.domain.failures

sealed interface ReadingsLookupFailure
data class ReadingsLookupError(val exception: Exception) : ReadingsLookupFailure
