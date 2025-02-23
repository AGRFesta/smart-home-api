package org.agrfesta.sh.api.domain.failures

sealed interface GetPersistedCacheEntryFailure: Failure
data object PersistedCacheEntryNotFound: GetPersistedCacheEntryFailure
