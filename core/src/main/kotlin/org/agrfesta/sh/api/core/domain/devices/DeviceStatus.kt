package org.agrfesta.sh.api.core.domain.devices

/**
 * Represents the current reachability status of a [Device] within the system.
 *
 * Status is updated on every synchronization cycle ([RefreshDevicesUseCase]).
 * It reflects the last known state reported by the provider, not a permanent domain event —
 * transitions are reversible and driven entirely by provider snapshots.
 */
enum class DeviceStatus {

    /**
     * The device is active and reachable through its provider.
     * Assigned when a provider includes the device in its snapshot during synchronization,
     * or when a previously [DETACHED] device reappears.
     */
    PAIRED,

    /**
     * The device is temporarily unreachable — either absent from the provider's latest snapshot
     * or the provider itself failed to respond during the last synchronization cycle.
     *
     * This is not a permanent state: a subsequent successful synchronization that includes
     * the device will automatically transition it back to [PAIRED].
     */
    DETACHED
}
