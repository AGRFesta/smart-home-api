package org.agrfesta.sh.api.core.application.usecases.heating

import org.springframework.stereotype.Service
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Serializes the heating evaluation and the heating disable, so that a disable takes effect after an
 * evaluation in progress. ❗️ The lock lives in memory: it serializes them within one application instance only.
 */
@Service
class HeatingActuationLock {
    private val lock = ReentrantLock()

    fun <T> withLock(action: () -> T): T = lock.withLock(action)
}
