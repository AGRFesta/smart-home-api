package org.agrfesta.sh.api.persistence.jdbc.adapters

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.failures.AreaCreationFailure
import org.agrfesta.sh.api.core.domain.failures.AreaDeletionFailure
import org.agrfesta.sh.api.core.domain.failures.AreaFetchFailure
import org.agrfesta.sh.api.core.domain.failures.AreaNameConflict
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.AreaUpdateFailure
import org.agrfesta.sh.api.core.domain.failures.GetAreasFailure
import org.agrfesta.sh.api.persistence.SameNameAreaException
import org.agrfesta.sh.api.persistence.jdbc.repositories.AreasJdbcRepository
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AreasJdbcAdapter(
    private val areasRepo: AreasJdbcRepository
) : AreasRepository {

    private val logger by LoggerDelegate()

    override fun getAreaById(areaId: UUID): Either<AreaFetchFailure, AreaDto> = try {
        areasRepo.findAreaById(areaId)?.asArea()?.right()
            ?: AreaNotFound(missingAreaId = areaId).left()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error in AreasJdbcAdapter", e)
        AreaRepositoryError.left()
    }

    override fun findAreaByName(name: String): Either<AreaRepositoryError, AreaDto?> = try {
        areasRepo.findAreaByName(name)?.asArea().right()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error in AreasJdbcAdapter", e)
        AreaRepositoryError.left()
    }

    override fun save(area: AreaDto): Either<AreaCreationFailure, Unit> = try {
        areasRepo.persist(area).right()
    } catch (_: SameNameAreaException) {
        AreaNameConflict.left()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error in AreasJdbcAdapter", e)
        AreaRepositoryError.left()
    }

    override fun getAll(): Either<GetAreasFailure, Collection<AreaDto>> = try {
        areasRepo.getAll().map { it.asArea() }.right()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error in AreasJdbcAdapter", e)
        AreaRepositoryError.left()
    }

    override fun update(area: AreaDto): Either<AreaUpdateFailure, AreaDto> = try {
        if (areasRepo.update(area) == 0) { AreaNotFound(missingAreaId = area.uuid).left() } else { area.right() }
    } catch (_: SameNameAreaException) {
        AreaNameConflict.left()
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error in AreasJdbcAdapter", e)
        AreaRepositoryError.left()
    }

    override fun deleteAreaById(areaId: UUID): Either<AreaDeletionFailure, Unit> = try {
        if (areasRepo.deleteAreaById(areaId) == 0) { AreaNotFound(missingAreaId = areaId).left() } else { Unit.right() }
    } catch (e: DataAccessException) {
        logger.error("Unexpected persistence error in AreasJdbcAdapter", e)
        AreaRepositoryError.left()
    }
}
