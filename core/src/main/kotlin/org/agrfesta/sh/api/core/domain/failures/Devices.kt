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

data class DeviceNotFound(
    val missingDeviceId: UUID
) : DeviceFetchFailure,
    DeviceUpdateFailure,
    GetDeviceFailure,
    InspectDeviceFailure,
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
    InspectDeviceFailure

/** No diagnostics implementation is registered for the device's provider. */
data object DiagnosticsNotSupported : InspectDeviceFailure

/** The diagnostics provider was reached but failed or is unreachable; [message] surfaces the cause. */
data class DiagnosticsProviderFailure(val message: String?) : InspectDeviceFailure
