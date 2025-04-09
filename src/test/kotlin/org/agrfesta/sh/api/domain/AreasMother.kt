package org.agrfesta.sh.api.domain

import java.util.*
import org.agrfesta.test.mothers.aRandomUniqueString

fun anArea(
    uuid: UUID = UUID.randomUUID(),
    name: String = aRandomUniqueString(),
    isIndoor: Boolean = true
) = Area(uuid, name, isIndoor)
