package org.agrfesta.sh.api.providers.switchbot

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.engine.okhttp.OkHttpEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMessageBuilder
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.TimeProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
@ConditionalOnSwitchBot
class SwitchBotDevicesClient(
    private val config: SwitchBotConfiguration,
    private val mapper: ObjectMapper,
    private val timeProvider: TimeProvider,
    private val randomGenerator: RandomGenerator,
    @Autowired(required = false) engine: HttpClientEngine = OkHttpEngine(OkHttpConfig())
) {
    private val client = HttpClient(engine) {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }
    }

    suspend fun getDevices(): JsonNode {
        val content = client.get("${config.baseUrl}/devices") {
            switchbotHeaders()
        }.bodyAsText()
        return mapper.readTree(content)
    }

    suspend fun getDeviceStatus(deviceId: String): JsonNode {
        val content = client.get("${config.baseUrl}/devices/$deviceId/status") {
            switchbotHeaders()
        }.bodyAsText()
        return mapper.readTree(content)
    }

    private fun HttpMessageBuilder.switchbotHeaders(): HeadersBuilder {
        val header = generateRequestHeader()
        val block: HeadersBuilder.() -> Unit = {
            append(HttpHeaders.Authorization, header.token)
            append("sign", header.signature)
            append("nonce", header.nonce)
            append("t", header.time)
        }
        return headers.apply(block)
    }

    private fun generateRequestHeader(): SwitchbotRequestHeader {
        val nonce = randomGenerator.string()
        val time = timeProvider.now().toEpochMilli().toString()
        val data = config.token + time + nonce
        val secretKeySpec = SecretKeySpec(config.secret.toByteArray(charset("UTF-8")), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKeySpec)
        val signature = String(
            Base64.getEncoder()
                .encode(
                    mac.doFinal(
                        data.toByteArray(charset("UTF-8"))
                    )
                )
        )
        return SwitchbotRequestHeader(
            token = config.token,
            signature = signature,
            nonce = nonce,
            time = time
        )
    }
}

private const val REQUEST_TIMEOUT_MILLIS = 60_000L

private data class SwitchbotRequestHeader(
    val token: String,
    val signature: String,
    val nonce: String,
    val time: String
)
