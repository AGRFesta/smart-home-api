package org.agrfesta.sh.api.core.domain.alerts

import java.util.UUID
import org.agrfesta.sh.api.core.domain.devices.Provider as DeviceProvider

/**
 * What an [Alert] is about. Modelled as a sealed type so that an [AlertScope] can never be paired with a
 * mismatching reference: each variant carries exactly the data its scope requires (and [Global] none).
 */
sealed interface AlertTarget {

    /** The scope this target belongs to (derived, never stored independently). */
    val scope: AlertScope

    /** Stable string identity of the target — its natural-key component; `null` for [Global]. */
    val reference: String?

    /** Concerns a single device, identified by its uuid. */
    data class Device(val deviceId: UUID) : AlertTarget {
        override val scope: AlertScope get() = AlertScope.DEVICE
        override val reference: String get() = deviceId.toString()
    }

    /** Concerns a whole provider. */
    data class Provider(val provider: DeviceProvider) : AlertTarget {
        override val scope: AlertScope get() = AlertScope.PROVIDER
        override val reference: String get() = provider.name
    }

    /** Concerns the system as a whole; no reference. */
    data object Global : AlertTarget {
        override val scope: AlertScope get() = AlertScope.GLOBAL
        override val reference: String? get() = null
    }

    companion object {

        /** Rebuilds an [AlertTarget] from its persisted [scope] and [reference]. */
        fun of(scope: AlertScope, reference: String?): AlertTarget = when (scope) {
            AlertScope.DEVICE -> Device(UUID.fromString(requireReference(scope, reference)))
            AlertScope.PROVIDER -> Provider(DeviceProvider.valueOf(requireReference(scope, reference)))
            AlertScope.GLOBAL -> Global
        }

        private fun requireReference(scope: AlertScope, reference: String?): String =
            requireNotNull(reference) { "$scope alert target requires a reference" }
    }
}
