package org.agrfesta.sh.api.persistence.jdbc.repositories

import java.sql.Timestamp
import java.time.temporal.ChronoUnit
import org.agrfesta.sh.api.domain.commons.CacheEntry
import org.agrfesta.sh.api.utils.TimeService
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class CacheJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val timeService: TimeService
) {

    fun upsert(key: String, value: String, ttl: Long? = null) {
        val sql = """
            INSERT INTO smart_home.cache (key, value, created_at, expires_at)
            VALUES (:key, :value, :createdAt, :expiresAt)
            ON CONFLICT (key) DO UPDATE
            SET value = EXCLUDED.value, expires_at = EXCLUDED.expires_at
        """.trimIndent()
        val createdAt = Timestamp.from(timeService.now())
        val expiresAt = ttl?.let { Timestamp.from(timeService.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(it)) }
        val params = MapSqlParameterSource()
            .addValue("key", key)
            .addValue("value", value)
            .addValue("createdAt", createdAt)
            .addValue("expiresAt", expiresAt)
        jdbcTemplate.update(sql, params)
    }

    fun findEntry(key: String): CacheEntry? {
        val sql = """
            SELECT value, expires_at FROM smart_home.cache
            WHERE key = :key AND (expires_at IS NULL OR expires_at > :currentTime)
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("key", key)
            .addValue("currentTime", Timestamp.from(timeService.now()))
        return jdbcTemplate.query(sql, params) { rs, _ ->
            CacheEntry(
                value = rs.getString("value"),
                expiresAt = rs.getTimestamp("expires_at")?.toInstant())
        }.firstOrNull()
    }

}
