package org.agrfesta.sh.api.core.application.devices

import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicePrototype
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceModel

class DeviceModelCatalog(prototypes: List<DevicePrototype>) {

    private val byModel: Map<DeviceModel, DevicePrototype> = prototypes.associateBy { it.model }

    init {
        require(byModel.size == prototypes.size) {
            "Duplicate device model prototypes: " +
                "${prototypes.groupBy { it.model }.filterValues { it.size > 1 }.keys}"
        }
    }

    fun prototypeOf(model: DeviceModel): DevicePrototype? = byModel[model]

    /**
     * Roles the given [model] can play in an area, resolved from its prototype. Returns an empty set
     * when [model] is null (device not yet re-synced) or unknown to the catalog.
     */
    fun rolesOf(model: DeviceModel?): Set<DeviceFeature> = model?.let { prototypeOf(it)?.roles }.orEmpty()
}
