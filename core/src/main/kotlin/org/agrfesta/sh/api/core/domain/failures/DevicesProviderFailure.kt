package org.agrfesta.sh.api.core.domain.failures

sealed interface DevicesProviderFailure
data class DevicesProviderError(val exception: Exception) : DevicesProviderFailure
