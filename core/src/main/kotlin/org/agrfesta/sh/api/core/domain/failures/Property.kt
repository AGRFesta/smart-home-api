package org.agrfesta.sh.api.core.domain.failures

sealed interface GetPropertyFailure

sealed interface FindPropertyFailure
data object PropertyNotFound: GetPropertyFailure
