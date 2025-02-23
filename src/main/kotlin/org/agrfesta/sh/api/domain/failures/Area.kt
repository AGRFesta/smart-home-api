package org.agrfesta.sh.api.domain.failures

sealed interface GetAreaFailure: AssociationFailure
sealed interface AreaCreationFailure

data object AreaNotFound: GetAreaFailure
data object AreaNameConflict: AreaCreationFailure
