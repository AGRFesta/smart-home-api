package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.domain.commons.PropertyEntry
import org.agrfesta.sh.api.core.domain.failures.FindPropertyFailure
import org.agrfesta.sh.api.core.domain.failures.GetPropertyFailure
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.domain.failures.PropertyNotFound
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.PropertyRepository
import org.agrfesta.sh.api.persistence.PropertyEntryDto
import org.agrfesta.sh.api.persistence.jdbc.repositories.PropertyJdbcRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

@Service
class PropertyJdbcAdapter(
    private val propertyJdbcRepository: PropertyJdbcRepository
): PropertyRepository {

    override fun upsert(key: String, value: String, ttl: Long?): Either<PersistenceFailure, Unit> = try {
        propertyJdbcRepository.upsert(key, value, ttl).right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun upsertBatch(entries: List<PropertyEntryDto>): Either<PersistenceFailure, Unit> = try {
        propertyJdbcRepository.upsertBatch(entries).right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun findEntry(key: String): Either<FindPropertyFailure, PropertyEntry?> = try {
        propertyJdbcRepository.findEntry(key).right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun getEntry(key: String): Either<GetPropertyFailure, PropertyEntry> = try {
        propertyJdbcRepository.findEntry(key)?.right()
            ?: PropertyNotFound.left()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

}
