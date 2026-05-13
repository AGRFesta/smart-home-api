package org.agrfesta.sh.api.core.domain.failures

sealed interface GetPropertyFailure: Failure
sealed interface FindPropertyFailure
data object PropertyNotFound: GetPropertyFailure
