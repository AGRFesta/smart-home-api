package org.agrfesta.sh.api.providers.hon

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnHon
@EnableConfigurationProperties(HonConfiguration::class)
class HonConfig
