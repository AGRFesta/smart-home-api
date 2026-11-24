package org.agrfesta.sh.api.core.domain.heating

import java.util.UUID

/** Outcome of switching heating on or off; [failedHeaters] are the heaters whose OFF command failed. */
data class SetHeatingEnabledReport(val failedHeaters: Set<UUID>)
