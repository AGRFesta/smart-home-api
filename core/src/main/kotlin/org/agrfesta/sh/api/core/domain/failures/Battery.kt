package org.agrfesta.sh.api.core.domain.failures

sealed interface BatteryLookupFailure
data class BatteryLookupError(val exception: Exception) : BatteryLookupFailure

sealed interface BatterySaveFailure
data class BatterySaveError(val exception: Exception) : BatterySaveFailure
