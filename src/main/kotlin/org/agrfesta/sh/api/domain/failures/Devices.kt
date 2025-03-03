package org.agrfesta.sh.api.domain.failures

import org.agrfesta.sh.api.domain.devices.Device

/**
 * Groups all causes of a failure fetching a [Device].
 */
sealed interface GetDeviceFailure: SensorAssignmentFailure, ActuatorAssignmentFailure

data object DeviceNotFound: GetDeviceFailure
