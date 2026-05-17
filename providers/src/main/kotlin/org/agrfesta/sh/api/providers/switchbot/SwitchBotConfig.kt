package org.agrfesta.sh.api.providers.switchbot

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnSwitchBot
@EnableConfigurationProperties(SwitchBotConfiguration::class)
class SwitchBotConfig
