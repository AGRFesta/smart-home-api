package org.agrfesta.sh.api.providers.hon.devices

import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceDriver
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.providers.hon.HonApplianceStore
import java.util.UUID

/**
 * Driver of the hOn AC appliances. Readings and commands arrive with #250/#251: for now it
 * carries the identity plus the [appliances] store, where the device sync preserves the
 * fields the command endpoints will need.
 */
class HonAc(
    override val uuid: UUID,
    override val deviceProviderId: String,
    val appliances: HonApplianceStore,
) : DeviceDriver {
    override val provider: Provider = Provider.HON
}
