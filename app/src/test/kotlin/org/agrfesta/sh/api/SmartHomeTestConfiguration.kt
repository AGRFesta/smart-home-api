package org.agrfesta.sh.api

import org.agrfesta.sh.api.controllers.createMockEngine
import org.agrfesta.sh.api.providers.netatmo.BehaviorRegistry
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class SmartHomeTestConfiguration {

    @Bean fun registry() = BehaviorRegistry()

    @Bean fun engine(registry: BehaviorRegistry) = createMockEngine(registry)

}
