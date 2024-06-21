package org.agrfesta.sh.api.providers.switchbot

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.agrfesta.sh.api.domain.Device
import org.agrfesta.sh.api.domain.DeviceStatus
import org.agrfesta.sh.api.domain.Provider
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class SwitchBotDevicesClient(
    private val config: SwitchBotConfiguration,
    private val mapper: ObjectMapper
    //@Autowired(required = false) engine: HttpClientEngine = OkHttpEngine(OkHttpConfig())
) {
    private val client = HttpClient(OkHttpEngine(OkHttpConfig())) {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
        }
    }

    suspend fun getDevices(): Collection<Device> {
        val header = generateRequestHeader()
        val content = client.get("${config.baseUrl}/devices") {
            headers {
                append(HttpHeaders.Authorization, header.token)
                append("sign", header.signature)
                append("nonce", header.nonce)
                append("t", header.time)
            }
        }.bodyAsText()
        val actualObj: JsonNode = mapper.readTree(content)
        val devices = actualObj.at("/body/deviceList") as ArrayNode
        return devices
            .map { mapper.treeToValue(it, SwitchBotDevice::class.java) }
            .map { Device(
                providerId = it.deviceId,
                provider = Provider.SWITCHBOT,
                status = DeviceStatus.PAIRED,
                name = it.deviceName
            ) }
    }

    private fun generateRequestHeader(): SwitchbotRequestHeader {
        val nonce = UUID.randomUUID().toString()
        val time = Instant.now().toEpochMilli().toString()
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

private data class SwitchbotRequestHeader(
    val token: String,
    val signature: String,
    val nonce: String,
    val time: String
)
