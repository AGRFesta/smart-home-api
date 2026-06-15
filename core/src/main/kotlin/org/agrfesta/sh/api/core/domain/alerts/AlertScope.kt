package org.agrfesta.sh.api.core.domain.alerts

/**
 * What an [Alert] is about, i.e. how to interpret its `target` reference.
 */
enum class AlertScope {

    /** The alert concerns a single device; `target` holds the device uuid. */
    DEVICE,

    /** The alert concerns a whole provider; `target` holds the provider identifier. */
    PROVIDER,

    /** The alert concerns the system as a whole; `target` is absent. */
    GLOBAL
}
