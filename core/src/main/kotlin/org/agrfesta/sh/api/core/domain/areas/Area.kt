package org.agrfesta.sh.api.core.domain.areas

import java.util.UUID

/**
 * Domain model of an area of the home.
 *
 * Query-only shapes (e.g. the home dashboard) use the read-models in
 * `core/application/readmodels/areas` instead.
 */
data class Area(
    val uuid: UUID,
    val name: String,
    val isIndoor: Boolean
)
