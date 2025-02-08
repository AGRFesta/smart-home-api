package org.agrfesta.sh.api.domain.commons

import java.time.Instant

data class CacheEntry(
    val value: String,
    val expiresAt: Instant? = null
)
