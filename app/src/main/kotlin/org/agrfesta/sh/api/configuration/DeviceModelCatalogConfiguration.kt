package org.agrfesta.sh.api.configuration

import org.agrfesta.sh.api.core.application.devices.DeviceModelCatalog
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicePrototype
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DeviceModelCatalogConfiguration {

    /**
     * Assembles the [DeviceModelCatalog] from every [DevicePrototype] bean on the classpath. Prototypes
     * are unconditional, so the catalog is complete even when a provider is disabled.
     */
    @Bean
    fun deviceModelCatalog(prototypes: List<DevicePrototype>): DeviceModelCatalog = DeviceModelCatalog(prototypes)
}
