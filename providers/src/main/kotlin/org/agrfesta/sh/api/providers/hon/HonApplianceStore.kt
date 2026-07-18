package org.agrfesta.sh.api.providers.hon

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory registry of the appliance-list fields the hOn command endpoints need
 * ([HonApplianceRef]), keyed by mac address. [HonService] refreshes it on every device
 * sync so factory-created drivers can reach those fields without a second list call.
 */
@Service
@ConditionalOnHon
class HonApplianceStore {
    private val refs = ConcurrentHashMap<String, HonApplianceRef>()

    fun save(ref: HonApplianceRef) {
        refs[ref.macAddress] = ref
    }

    fun refOf(macAddress: String): HonApplianceRef? = refs[macAddress]
}
