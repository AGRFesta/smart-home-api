package org.agrfesta.sh.api.persistence.jdbc.dao

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.UUID
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.failures.AreaCreationFailure
import org.agrfesta.sh.api.core.domain.failures.AreaDeletionFailure
import org.agrfesta.sh.api.core.domain.failures.AreaFetchFailure
import org.agrfesta.sh.api.core.domain.failures.AreaNameConflict
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.AreasRepository
import org.agrfesta.sh.api.persistence.SameNameAreaException
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasJdbcRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

@Service
 class AreasJdbcAdapter(
    private val areasRepo: AreasJdbcRepository
): AreasRepository {

    override fun getAreaById(areaId: UUID): Either<AreaFetchFailure, AreaDto> = try {
        areasRepo.findAreaById(areaId)?.asArea()?.right()
            ?: AreaNotFound(missingAreaId = areaId).left()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun findAreaByName(name: String): Either<PersistenceFailure, AreaDto?> = try {
        areasRepo.findAreaByName(name)?.asArea().right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun save(area: AreaDto): Either<AreaCreationFailure, Unit> = try {
        areasRepo.persist(area).right()
    } catch (_: SameNameAreaException) {
        AreaNameConflict.left()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun getAll(): Either<PersistenceFailure, Collection<AreaDto>> = try {
        areasRepo.getAll().map { it.asArea() }.right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

    override fun deleteAreaById(areaId: UUID): Either<AreaDeletionFailure, Unit> = try {
        if (areasRepo.deleteAreaById(areaId) == 0) AreaNotFound(missingAreaId = areaId).left()
        else Unit.right()
    } catch (e: DataAccessException) {
        PersistenceFailure(e).left()
    }

}
