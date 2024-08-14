package org.agrfesta.sh.api.utils

import org.springframework.stereotype.Service
import java.util.*

interface RandomGenerator {

    fun string(): String
    fun uuid(): UUID

}

@Service
class RandomGeneratorImpl: RandomGenerator {
    override fun string(): String = uuid().toString()
    override fun uuid(): UUID = UUID.randomUUID()
}
