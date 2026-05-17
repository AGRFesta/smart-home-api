package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.commons.PropertyUpsertEntry
import org.agrfesta.sh.api.core.domain.failures.FindPropertyFailure
import org.agrfesta.sh.api.core.domain.failures.GetPropertyFailure
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.sh.api.core.domain.failures.PropertyRepositoryError
import org.agrfesta.sh.api.persistence.jdbc.repositories.PropertyJdbcRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

@Service
class PropertyJdbcAdapter(
    private val propertyJdbcRepository: PropertyJdbcRepository
) : PropertyRepository {
    private val logger by LoggerDelegate()

    override fun upsert(key: String, value: String, ttl: Long?): Either<PropertyRepositoryError, Unit> = try {
        propertyJdbcRepository.upsert(key, value, ttl).right()
    } catch (e: DataAccessException) {
        logger.error("Failed to upsert property entry for key '$key'", e)
        PropertyRepositoryError.left()
    }

    override fun upsertBatch(entries: List<PropertyUpsertEntry>): Either<PropertyRepositoryError, Unit> = try {
        propertyJdbcRepository.upsertBatch(entries).right()
    } catch (e: DataAccessException) {
        logger.error("Failed to upsert batch of ${entries.size} property entries", e)
        PropertyRepositoryError.left()
    }

    override fun findEntry(key: String): Either<FindPropertyFailure, PropertyEntry?> = try {
        propertyJdbcRepository.findEntry(key).right()
    } catch (e: DataAccessException) {
        logger.error("Failed to find property entry for key '$key'", e)
        PropertyRepositoryError.left()
    }

    override fun getEntry(key: String): Either<GetPropertyFailure, PropertyEntry> = try {
        propertyJdbcRepository.findEntry(key)?.right()
            ?: PropertyNotFound.left()
    } catch (e: DataAccessException) {
        logger.error("Failed to get property entry for key '$key'", e)
        PropertyRepositoryError.left()
    }
}
