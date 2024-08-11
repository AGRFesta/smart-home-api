package org.agrfesta.sh.api.utils

import org.springframework.stereotype.Service
import java.util.*

interface RandomGenerator {

    fun string(): String

}

@Service
class RandomGeneratorImpl: RandomGenerator {
    override fun string(): String = UUID.randomUUID().toString()
}
