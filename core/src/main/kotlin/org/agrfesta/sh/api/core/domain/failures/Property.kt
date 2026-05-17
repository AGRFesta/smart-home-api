package org.agrfesta.sh.api.core.domain.failures

sealed interface GetPropertyFailure

sealed interface FindPropertyFailure

sealed interface UpsertPropertyFailure

sealed interface UpsertPropertyBatchFailure

data object PropertyNotFound : GetPropertyFailure

data object PropertyRepositoryError :
    GetPropertyFailure,
    FindPropertyFailure,
    UpsertPropertyFailure,
    UpsertPropertyBatchFailure

data object EmptyPropertyBatch : UpsertPropertyBatchFailure

data class PropertyBatchTooLarge(val maxSize: Int) : UpsertPropertyBatchFailure

data object DuplicatePropertyKeys : UpsertPropertyBatchFailure
