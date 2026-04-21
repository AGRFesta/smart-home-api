package org.agrfesta.sh.api.core.domain.failures

data class ProviderFailure(override val exception: Exception): ExceptionFailure
