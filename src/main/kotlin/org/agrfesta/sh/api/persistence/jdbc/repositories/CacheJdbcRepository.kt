package org.agrfesta.sh.api.persistence.jdbc.repositories

import java.sql.Timestamp
import java.time.temporal.ChronoUnit
import org.agrfesta.sh.api.domain.commons.CacheEntry
import org.agrfesta.sh.api.persistence.CacheEntryDto
import org.agrfesta.sh.api.utils.TimeService
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.SqlParameterSource
import org.springframework.stereotype.Service

/**
 * Repository for managing cache entries in a JDBC-based database.
 *
 * This repository provides methods to insert or update cache entries and to retrieve them.
 * It handles the expiration logic based on the provided time-to-live (TTL) or expiration timestamp.
 *
 * @property jdbcTemplate The [NamedParameterJdbcTemplate] used for database operations.
 * @property timeService The [TimeService] used to obtain the current time for timestamp calculations.
 */
@Service
class CacheJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val timeService: TimeService
) {

    /**
     * Inserts or updates a cache entry.
     *
     * If an entry with the same key already exists, its value and expiration time are updated.
     *
     * @param key The unique key for the cache entry.
     * @param value The value to be cached.
     * @param ttl The time-to-live in seconds. If null, the entry does not expire automatically based on TTL.
     */
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

    /**
     * Inserts or updates a batch of cache entries.
     *
     * If an entry with the same key already exists, its value and expiration time are updated.
     * @param entries A list of [CacheEntryDto] objects to be inserted or updated.
     */
    fun upsertBatch(entries: List<CacheEntryDto>) {
        val sql = """
            INSERT INTO smart_home.cache (key, value, created_at, expires_at)
            VALUES (:key, :value, :createdAt, :expiresAt)
            ON CONFLICT (key) DO UPDATE
            SET value = EXCLUDED.value, expires_at = EXCLUDED.expires_at
        """.trimIndent()

        val now = timeService.now()
        val createdAt = Timestamp.from(now)
        val nowTruncated = now.truncatedTo(ChronoUnit.SECONDS)
        val params: Array<SqlParameterSource> = entries.map { entry ->
            val expiresAt = entry.ttl?.let {
                Timestamp.from(nowTruncated.plusSeconds(it))
            }
            MapSqlParameterSource()
                .addValue("key", entry.key)
                .addValue("value", entry.value)
                .addValue("createdAt", createdAt)
                .addValue("expiresAt", expiresAt)
        }.toTypedArray()

        jdbcTemplate.batchUpdate(sql, params)
    }

    /**
     * Retrieves a cache entry by its key.
     *
     * Only entries that have not expired are returned.
     *
     * @param key The key of the cache entry to retrieve.
     * @return The [CacheEntry] if found and valid, or null otherwise.
     */
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
