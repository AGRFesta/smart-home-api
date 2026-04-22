package org.agrfesta.sh.api.core.domain.commons

sealed interface FieldResult<out T>
data class FieldSuccess<out T>(val value: T) : FieldResult<T>
data class FieldFailure(val error: String) : FieldResult<Nothing>
