package org.agrfesta.sh.api.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate


@Configuration
class RedisConfig {
//    @Bean
//    fun jedisConnectionFactory(): JedisConnectionFactory {
//        val jedisConFactory = JedisConnectionFactory()
//        jedisConFactory.hostName = "localhost"
//        jedisConFactory.port = 6379
//        return jedisConFactory
//    }
//
//    @Bean
//    fun redisTemplate(): RedisTemplate<String, Any> {
//        val template = RedisTemplate<String, Any>()
//        template.connectionFactory = jedisConnectionFactory()
//        return template
//    }

    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<*, *> {
        val template: RedisTemplate<*, *> = RedisTemplate<Any, Any>()
        template.connectionFactory = connectionFactory
        return template
    }
}
