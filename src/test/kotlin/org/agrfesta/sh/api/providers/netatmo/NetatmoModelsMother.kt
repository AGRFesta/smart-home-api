package org.agrfesta.sh.api.providers.netatmo

import org.agrfesta.test.mothers.aRandomUniqueString

fun aNetatmoModule(
    id: String = aRandomUniqueString(),
    name: String = aRandomUniqueString(),
    type: String = "BNS",
    roomId: String = aRandomUniqueString()
) = NetatmoModule(id, type, name, roomId)
