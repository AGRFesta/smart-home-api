package org.agrfesta.sh.api.controllers

import org.agrfesta.sh.api.core.domain.areas.Area
import java.util.UUID

data class AreaResponse(
    val uuid: UUID,
    val name: String,
    val isIndoor: Boolean
)

fun Area.toResponse() = AreaResponse(uuid = uuid, name = name, isIndoor = isIndoor)
