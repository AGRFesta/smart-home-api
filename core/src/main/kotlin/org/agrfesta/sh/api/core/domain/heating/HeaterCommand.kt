package org.agrfesta.sh.api.core.domain.heating

/**
 * Desired command for a shared heater, as decided by a pure heating strategy.
 *
 * [NONE] means there is nothing to do / not enough data to decide (e.g. no areas).
 */
enum class HeaterCommand { ON, OFF, NONE }
