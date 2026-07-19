package org.agrfesta.sh.api.core.domain.failures

import org.agrfesta.sh.api.core.domain.devices.Device
import java.util.UUID

/**
 * Groups all causes of a failure fetching a [Device].
 */
sealed interface DeviceFetchFailure
sealed interface DeviceCreationFailure
sealed interface DeviceUpdateFailure
sealed interface GetDevicesFailure
sealed interface GetDeviceFailure
sealed interface InspectDeviceFailure
sealed interface GetAcStateFailure
sealed interface SetAcSettingsFailure

data class DeviceNotFound(
    val missingDeviceId: UUID
) : DeviceFetchFailure,
    DeviceUpdateFailure,
    GetDeviceFailure,
    InspectDeviceFailure,
    GetAcStateFailure,
    SetAcSettingsFailure,
    SensorAssignmentFailure,
    ActuatorAssignmentFailure,
    SensorUnassignFailure,
    ActuatorUnassignFailure

data object DeviceRepositoryError :
    DeviceFetchFailure,
    DeviceCreationFailure,
    DeviceUpdateFailure,
    GetDevicesFailure,
    GetDeviceFailure,
    InspectDeviceFailure,
    GetAcStateFailure,
    SetAcSettingsFailure

/** No diagnostics implementation is registered for the device's provider. */
data object DiagnosticsNotSupported : InspectDeviceFailure

/** The diagnostics provider was reached but failed or is unreachable; [message] surfaces the cause. */
data class DiagnosticsProviderFailure(val message: String?) : InspectDeviceFailure

/** The device exists but is not an air conditioner (or its provider has no driver factory). */
data object NotAnAirConditioner : GetAcStateFailure, SetAcSettingsFailure

/** The AC's provider was reached but failed or is unreachable; [message] surfaces the cause. */
data class AcProviderFailure(val message: String?) : GetAcStateFailure, SetAcSettingsFailure

/** The requested setting was rejected by the device's validation; [reason] explains why. */
data class InvalidAcSetting(val reason: String) : SetAcSettingsFailure
