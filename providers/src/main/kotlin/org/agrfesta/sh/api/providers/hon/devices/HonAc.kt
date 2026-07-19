package org.agrfesta.sh.api.providers.hon.devices

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.AirConditioner
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.Inspectable
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.AcFanSpeed
import org.agrfesta.sh.api.core.domain.devices.AcMode
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.ActuatorOperationFailure
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderError
import org.agrfesta.sh.api.core.domain.failures.DevicesProviderFailure
import org.agrfesta.sh.api.providers.hon.HonApiClient
import org.agrfesta.sh.api.providers.hon.HonApplianceStore
import org.agrfesta.sh.api.providers.hon.toException
import java.util.UUID

/**
 * Driver of the hOn AC appliances: every write is a read-modify-write of the `settings`
 * command (catalog + latest context via [AcSettingsCommand]), because hOn always transmits
 * ALL parameters. The [appliances] store carries the appliance-list fields the command
 * endpoints need; after a restart it is empty until the device sync runs.
 */
class HonAc(
    override val uuid: UUID,
    override val deviceProviderId: String,
    val appliances: HonApplianceStore,
    private val client: HonApiClient,
) : AirConditioner, Inspectable {
    override val provider: Provider = Provider.HON

    /**
     * The raw `GET /commands/v1/context` body, **verbatim** (pass-through like SwitchBot: no
     * parse -> re-serialize round trip), so the diagnostics endpoint exposes exactly what the
     * cloud sent.
     */
    override fun inspect(): Either<DevicesProviderFailure, String> =
        runBlocking {
            either {
                val ref = ensureNotNull(appliances.refOf(deviceProviderId)) {
                    DevicesProviderError(IllegalStateException("Appliance '$deviceProviderId' not synced yet"))
                }
                client.rawContext(ref).mapLeft { DevicesProviderError(it.toException()) }.bind()
            }
        }

    override fun getActuatorStatus(): Either<ActuatorOperationFailure, ActuatorStatus> =
        runBlocking {
            either {
                val ref = ensureNotNull(appliances.refOf(deviceProviderId)) { AcApplianceNotSynced }
                val context = client.loadAttributes(ref).mapLeft(::HonAcProviderFailure).bind()
                when (context.at("/shadow/parameters/onOffStatus/parNewVal").asText()) {
                    "1" -> ActuatorStatus.ON
                    "0" -> ActuatorStatus.OFF
                    else -> ActuatorStatus.UNDEFINED
                }
            }
        }

    override fun on(): Either<ActuatorOperationFailure, Unit> = applySetting("onOffStatus", "1")

    override fun off(): Either<ActuatorOperationFailure, Unit> = applySetting("onOffStatus", "0")

    override fun setMode(mode: AcMode): Either<ActuatorOperationFailure, Unit> =
        applySetting("machMode", mode.honCode())

    override fun setTargetTemperature(temperature: Temperature): Either<ActuatorOperationFailure, Unit> =
        applySetting("tempSel", temperature.value.toPlainString())

    override fun setFanSpeed(speed: AcFanSpeed): Either<ActuatorOperationFailure, Unit> =
        applySetting("windSpeed", speed.honCode())

    /** One read-modify-write round: catalog + context -> validated overlay -> send. */
    private fun applySetting(parameter: String, value: String): Either<ActuatorOperationFailure, Unit> =
        runBlocking {
            either {
                val ref = ensureNotNull(appliances.refOf(deviceProviderId)) { AcApplianceNotSynced }
                val catalog = client.loadCommands(ref).mapLeft(::HonAcProviderFailure).bind()
                val context = client.loadAttributes(ref).mapLeft(::HonAcProviderFailure).bind()
                val command = AcSettingsCommand(catalog, context).with(parameter, value).bind()
                client.sendCommand(
                    appliance = ref,
                    command = SETTINGS_COMMAND,
                    parameters = command.parameters(),
                    ancillaryParameters = command.ancillaryParameters(),
                ).mapLeft(::HonAcProviderFailure).bind()
            }
        }

    /**
     * Domain mode -> hOn `machMode` code. Protocol constants (ported from addhOn's
     * `AC_MODE_MAP`): the catalog only carries the admitted codes per firmware, never their
     * semantics — [applySetting] still validates the code against the device's enum.
     */
    private fun AcMode.honCode(): String = when (this) {
        AcMode.AUTO -> "0"
        AcMode.COOL -> "1"
        AcMode.DRY -> "2"
        AcMode.HEAT -> "4"
        AcMode.FAN_ONLY -> "6"
    }

    /** Domain fan speed -> hOn `windSpeed` code (ported from addhOn's `AC_FAN_MAP`). */
    private fun AcFanSpeed.honCode(): String = when (this) {
        AcFanSpeed.HIGH -> "1"
        AcFanSpeed.MEDIUM -> "2"
        AcFanSpeed.LOW -> "3"
        AcFanSpeed.AUTO -> "5"
    }

    companion object {
        private const val SETTINGS_COMMAND = "settings"
    }
}
