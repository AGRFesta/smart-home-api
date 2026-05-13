package org.agrfesta.sh.api.providers.netatmo

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Response specification used by the registry.
 */
data class ResponseSpec(
    val content: String,
    val status: HttpStatusCode = HttpStatusCode.OK,
    val headers: Headers = headersOf(HttpHeaders.ContentType, "application/json")
)

/**
 * A behavior entry: matcher + queue of responses.
 */
data class Behavior(
    val matcher: (HttpRequestData) -> Boolean,
    val responses: ArrayDeque<ResponseSpec>
)

/**
 * Registry that tests will mutate to declare behaviours.
 *
 * This is thread-safe enough for tests: it records requests and allows
 * runtime registration of behaviors and sequences of responses.
 */
class BehaviorRegistry {
    private val behaviors = CopyOnWriteArrayList<Behavior>()
    private val recordedRequests = mutableListOf<HttpRequestData>()
    private val mutex = Mutex()

    private fun recordRequest(req: HttpRequestData) {
        synchronized(recordedRequests) { recordedRequests += req }
    }

    /**
     * Register a behavior. Provide one or more responses (sequence).
     */
    fun given(matcher: (HttpRequestData) -> Boolean, vararg responses: ResponseSpec) {
        val deque = ArrayDeque(responses.toList())
        behaviors += Behavior(matcher, deque)
    }

    suspend fun verifyRequest(
        method: HttpMethod,
        path: String,
        times: Int = 1,
        requestAssertion: suspend (HttpRequestData) -> Unit = {}
    ) {
        val matching = recordedRequests.filter {
            it.method == method && it.url.encodedPath == path
        }
        matching.size shouldBe times
        if (matching.isNotEmpty()) {
            requestAssertion(matching.last())
        }
    }

    /**
     * Try to find a matching behavior and pop the next ResponseSpec.
     * If no behavior matches, returns null.
     */
    suspend fun matchAndPop(request: HttpRequestData): ResponseSpec? {
        recordRequest(request)
        // Find first match
        val b = behaviors.firstOrNull { it.matcher(request) } ?: return null

        // synchronize access to the deque for this behavior
        return mutex.withLock {
            if (b.responses.isEmpty()) {
                // policy: reuse last response if exhausted (or return null to fail)
                b.responses.lastOrNull()
            } else {
                b.responses.removeFirst()
            }
        }
    }

    fun clear() {
        behaviors.clear()
        synchronized(recordedRequests) { recordedRequests.clear() }
    }

}

suspend fun HttpRequestData.getBodyAsString(): String = body.toByteArray().decodeToString()
