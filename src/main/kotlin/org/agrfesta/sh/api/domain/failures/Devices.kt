package org.agrfesta.sh.api.domain.failures

import org.agrfesta.sh.api.domain.devices.DeviceDto

/**
 * Groups all causes of a failure fetching a [DeviceDto].
 */
sealed interface GetDeviceFailure: SensorAssignmentFailure, ActuatorAssignmentFailure

data object DeviceNotFound: GetDeviceFailure
