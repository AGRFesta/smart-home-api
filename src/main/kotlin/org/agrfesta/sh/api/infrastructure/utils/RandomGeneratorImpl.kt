package org.agrfesta.sh.api.infrastructure.utils

import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RandomGeneratorImpl : RandomGenerator {
    override fun string(): String = uuid().toString()
    override fun uuid(): UUID = UUID.randomUUID()
}
