package org.agrfesta.sh.api.core.application.ports.outbounds

import java.util.UUID

interface RandomGenerator {

    fun string(): String
    fun uuid(): UUID

}
