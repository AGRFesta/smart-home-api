package org.agrfesta.sh.api.providers.hon.devices

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.AirConditioner
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.Inspectable
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.AcPowerCommand
import org.agrfesta.sh.api.core.domain.devices.AcSettingsUpdate
import org.agrfesta.sh.api.core.domain.devices.AcState
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

    override fun getState(): Either<ActuatorOperationFailure, AcState> =
        runBlocking {
            either {
                val ref = ensureNotNull(appliances.refOf(deviceProviderId)) { AcApplianceNotSynced }
                val context = client.loadAttributes(ref).mapLeft(::HonAcProviderFailure).bind()
                val parameters = context.at("/shadow/parameters")
                AcState(
                    power = when (parameters.at("/onOffStatus/parNewVal").asText()) {
                        "1" -> ActuatorStatus.ON
                        "0" -> ActuatorStatus.OFF
                        else -> ActuatorStatus.UNDEFINED
                    },
                    mode = parameters.at("/machMode/parNewVal").asText().toAcMode(),
                    targetTemperature = parameters.at("/tempSel/parNewVal").asText()
                        .toBigDecimalOrNull()?.let { Temperature.of(it) },
                    fanSpeed = parameters.at("/windSpeed/parNewVal").asText().toAcFanSpeed(),
                )
            }
        }

    override fun getActuatorStatus(): Either<ActuatorOperationFailure, ActuatorStatus> =
        getState().map { it.power }

    override fun updateSettings(update: AcSettingsUpdate): Either<ActuatorOperationFailure, Unit> =
        applySettings(update.honChanges())

    override fun on(): Either<ActuatorOperationFailure, Unit> =
        updateSettings(AcSettingsUpdate(power = AcPowerCommand.ON))

    override fun off(): Either<ActuatorOperationFailure, Unit> =
        updateSettings(AcSettingsUpdate(power = AcPowerCommand.OFF))

    /** One read-modify-write round: catalog + context -> validated overlays -> ONE send. */
    private fun applySettings(changes: Map<String, String>): Either<ActuatorOperationFailure, Unit> =
        runBlocking {
            either {
                val ref = ensureNotNull(appliances.refOf(deviceProviderId)) { AcApplianceNotSynced }
                val catalog = client.loadCommands(ref).mapLeft(::HonAcProviderFailure).bind()
                val context = client.loadAttributes(ref).mapLeft(::HonAcProviderFailure).bind()
                val command = changes.entries.fold(AcSettingsCommand(catalog, context)) { cmd, (parameter, value) ->
                    cmd.with(parameter, value).bind()
                }
                client.sendCommand(
                    appliance = ref,
                    command = SETTINGS_COMMAND,
                    parameters = command.parameters(),
                    ancillaryParameters = command.ancillaryParameters(),
                ).mapLeft(::HonAcProviderFailure).bind()
            }
        }

    companion object {
        private const val SETTINGS_COMMAND = "settings"
    }
}
