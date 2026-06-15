package org.agrfesta.sh.api.core.domain.alerts

/**
 * The kind of condition an [Alert] tracks.
 *
 * Designed to be extended (e.g. `SENSOR_STALE`, ...). The [Alert] aggregate stays agnostic of the
 * concrete semantics behind each type: the rule that evaluates the condition owns them.
 */
enum class AlertType {

    /** A device's battery level dropped below the configured threshold. */
    BATTERY_LOW,

    /** A device is no longer reachable through its provider. */
    DEVICE_DETACHED
}
