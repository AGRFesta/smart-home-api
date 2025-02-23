package org.agrfesta.sh.api.domain.failures

data class ProviderFailure(override val exception: Exception): ExceptionFailure
