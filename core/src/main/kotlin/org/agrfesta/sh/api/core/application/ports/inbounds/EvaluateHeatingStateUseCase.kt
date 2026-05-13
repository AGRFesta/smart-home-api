package org.agrfesta.sh.api.core.application.ports.inbounds

interface EvaluateHeatingStateUseCase {

    /**
     * Evaluates the current heating state across all heatable areas and drives actuators accordingly.
     *
     * Reads the `heating.enabled` flag from the property store; if disabled or unreadable, returns
     * immediately without touching any device. Otherwise fetches devices and areas, groups heatable
     * areas by shared heater, and delegates control to the configured heating strategy.
     */
    fun execute()

}
