package org.agrfesta.sh.api.providers.netatmo

import org.agrfesta.sh.api.core.domain.devices.ActuatorOperationFailure
import org.agrfesta.sh.api.core.domain.devices.SensorReadingsFailure
import org.agrfesta.sh.api.core.domain.failures.GetPropertyFailure

sealed interface NetatmoClientFailure : SensorReadingsFailure, ActuatorOperationFailure

data class NetatmoPropertyError(val failure: GetPropertyFailure) : NetatmoClientFailure
