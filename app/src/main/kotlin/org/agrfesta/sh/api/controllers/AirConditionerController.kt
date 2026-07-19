package org.agrfesta.sh.api.controllers

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.devices.GetAcStateUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.devices.SetAcSettingsUseCase
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.AcFanSpeed
import org.agrfesta.sh.api.core.domain.devices.AcMode
import org.agrfesta.sh.api.core.domain.devices.AcPowerCommand
import org.agrfesta.sh.api.core.domain.devices.AcSettingsUpdate
import org.agrfesta.sh.api.core.domain.devices.AcState
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.failures.AcProviderFailure
import org.agrfesta.sh.api.core.domain.failures.DeviceNotFound
import org.agrfesta.sh.api.core.domain.failures.DeviceRepositoryError
import org.agrfesta.sh.api.core.domain.failures.InvalidAcSetting
import org.agrfesta.sh.api.core.domain.failures.NotAnAirConditioner
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.internalServerError
import org.springframework.http.ResponseEntity.noContent
import org.springframework.http.ResponseEntity.notFound
import org.springframework.http.ResponseEntity.ok
import org.springframework.http.ResponseEntity.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/devices")
class AirConditionerController(
    private val getAcStateUseCase: GetAcStateUseCase,
    private val setAcSettingsUseCase: SetAcSettingsUseCase
) {
    @PatchMapping("/{uuid}/air-conditioner")
    fun setAcSettings(
        @PathVariable uuid: UUID,
        @RequestBody request: AcSettingsRequest
    ): ResponseEntity<Any> {
        val update = try {
            request.toUpdate()
        } catch (e: IllegalArgumentException) {
            return status(HttpStatus.BAD_REQUEST).body(MessageResponse(e.message ?: "Invalid request body"))
        }
        if (update == AcSettingsUpdate()) {
            return status(HttpStatus.BAD_REQUEST).body(MessageResponse("At least one field must be provided"))
        }
        return when (val result = setAcSettingsUseCase.execute(uuid, update)) {
            is Either.Right -> noContent().build()
            is Either.Left -> when (val failure = result.value) {
                is DeviceNotFound -> notFound().build()
                DeviceRepositoryError -> internalServerError()
                    .body(MessageResponse("Unable to retrieve device '$uuid'!"))
                NotAnAirConditioner -> conflictNotAnAc(uuid)
                is InvalidAcSetting -> status(HttpStatus.BAD_REQUEST)
                    .body(MessageResponse(failure.reason))
                is AcProviderFailure -> badGateway(failure.message, uuid)
            }
        }
    }

    @GetMapping("/{uuid}/air-conditioner")
    fun getAcState(@PathVariable uuid: UUID): ResponseEntity<Any> =
        when (val result = getAcStateUseCase.execute(uuid)) {
            is Either.Right -> ok(result.value.toResponse())
            is Either.Left -> when (val failure = result.value) {
                is DeviceNotFound -> notFound().build()
                DeviceRepositoryError -> internalServerError()
                    .body(MessageResponse("Unable to retrieve device '$uuid'!"))
                NotAnAirConditioner -> conflictNotAnAc(uuid)
                is AcProviderFailure -> badGateway(failure.message, uuid)
            }
        }

    private fun conflictNotAnAc(uuid: UUID): ResponseEntity<Any> =
        status(HttpStatus.CONFLICT).body(MessageResponse("Device '$uuid' is not an air conditioner!"))

    private fun badGateway(message: String?, uuid: UUID): ResponseEntity<Any> =
        status(HttpStatus.BAD_GATEWAY)
            .body(MessageResponse(message ?: "Air conditioner provider failed for device '$uuid'!"))
}

private fun AcSettingsRequest.toUpdate() = AcSettingsUpdate(
    power = power?.let { parseEnum<AcPowerCommand>("power", it) },
    mode = mode?.let { parseEnum<AcMode>("mode", it) },
    targetTemperature = targetTemperature?.let { Temperature.of(it) },
    fanSpeed = fanSpeed?.let { parseEnum<AcFanSpeed>("fanSpeed", it) },
)

private inline fun <reified E : Enum<E>> parseEnum(field: String, value: String): E =
    enumValues<E>().firstOrNull { it.name == value }
        ?: throw IllegalArgumentException(
            "Invalid $field '$value', allowed: ${enumValues<E>().joinToString()}",
        )

data class AcSettingsRequest(
    val power: String? = null,
    val mode: String? = null,
    val targetTemperature: BigDecimal? = null,
    val fanSpeed: String? = null
)

data class AcStateResponse(
    val power: ActuatorStatus,
    val mode: AcMode?,
    val targetTemperature: BigDecimal?,
    val fanSpeed: AcFanSpeed?
)

fun AcState.toResponse() = AcStateResponse(
    power = power,
    mode = mode,
    targetTemperature = targetTemperature?.value,
    fanSpeed = fanSpeed
)
