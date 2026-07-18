package org.agrfesta.sh.api.providers.hon

import arrow.core.Either
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesProvider
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.devices.ProviderDeviceData
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderError
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderFailure
import org.springframework.stereotype.Service

@Service
@ConditionalOnHon
class HonService(
    private val client: HonApiClient,
    private val store: HonApplianceStore
) : DevicesProvider {
    override val provider: Provider = Provider.HON

    override fun getAllDevices(): Either<DevicesProviderFailure, Collection<ProviderDeviceData>> = runBlocking {
        client.loadAppliances()
            .mapLeft { DevicesProviderError(it.toException()) }
            .map { appliances -> appliances.mapNotNull { it.toProviderDeviceData() } }
    }

    /** Defensive: an entry without the identity fields cannot become a device — skip it. */
    private fun JsonNode.toProviderDeviceData(): ProviderDeviceData? {
        val macAddress = textOrNull("macAddress") ?: return null
        val modelName = textOrNull("modelName") ?: return null
        store.save(toApplianceRef(macAddress))
        return ProviderDeviceData(
            deviceProviderId = macAddress,
            provider = provider,
            name = textOrNull("nickName")?.trim() ?: modelName,
            model = DeviceModel(MODEL_PREFIX + modelName),
        )
    }

    /** The list-entry fields the command endpoints need (#250/#251), kept for the factory. */
    private fun JsonNode.toApplianceRef(macAddress: String) = HonApplianceRef(
        macAddress = macAddress,
        applianceType = textOrNull("applianceTypeName").orEmpty(),
        applianceModelId = textOrNull("applianceModelId").orEmpty(),
        code = textOrNull("code") ?: codeFromSerialNumber(),
        firmwareId = textOrNull("eepromId"),
        fwVersion = textOrNull("fwVersion"),
        series = textOrNull("series"),
    )

    /** addhOn fallback: the code is the serial's first 8 chars, 11 when the serial is 18+ long. */
    private fun JsonNode.codeFromSerialNumber(): String {
        val serialNumber = textOrNull("serialNumber").orEmpty()
        val length = if (serialNumber.length < LONG_SERIAL_LENGTH) SHORT_SERIAL_CODE_LENGTH else SERIAL_CODE_LENGTH
        return serialNumber.take(length)
    }

    private fun JsonNode.textOrNull(field: String): String? =
        get(field)?.takeUnless { it.isNull }?.asText()?.takeIf { it.isNotBlank() }

    companion object {
        /** Provider qualifier of every hOn model string (e.g. `hon/AS25PBPHRA-PRE`). */
        const val MODEL_PREFIX = "hon/"

        /** Provider-qualified models of the known hOn AC appliances (captured in Phase 0). */
        const val AC_AS25PBPHRA_PRE_MODEL = MODEL_PREFIX + "AS25PBPHRA-PRE"
        const val AC_AS35PBPHRA_PRE_MODEL = MODEL_PREFIX + "AS35PBPHRA-PRE"

        private const val LONG_SERIAL_LENGTH = 18
        private const val SHORT_SERIAL_CODE_LENGTH = 8
        private const val SERIAL_CODE_LENGTH = 11
    }
}
