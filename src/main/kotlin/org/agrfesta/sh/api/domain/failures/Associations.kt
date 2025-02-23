package org.agrfesta.sh.api.domain.failures

sealed interface AssociationFailure

data object AssociationConflict: AssociationFailure
data object SameAreaAssociation: AssociationFailure
