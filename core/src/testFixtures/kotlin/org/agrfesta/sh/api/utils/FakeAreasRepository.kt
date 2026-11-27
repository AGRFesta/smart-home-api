package org.agrfesta.sh.api.utils

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.domain.areas.Area
import org.agrfesta.sh.api.core.domain.failures.AreaCreationFailure
import org.agrfesta.sh.api.core.domain.failures.AreaDeletionFailure
import org.agrfesta.sh.api.core.domain.failures.AreaFetchFailure
import org.agrfesta.sh.api.core.domain.failures.AreaNameConflict
import org.agrfesta.sh.api.core.domain.failures.AreaNotFound
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError
import org.agrfesta.sh.api.core.domain.failures.AreaUpdateFailure
import org.agrfesta.sh.api.core.domain.failures.GetAreasFailure
import java.util.UUID

/**
 * In-memory [AreasRepository] for state-based tests: arrange with [givenArea], assert on [storedArea]
 * and [storedAreas]. [AreasRepositoryContract] keeps its outcomes aligned with the JDBC adapter.
 * [givenReadFails] makes every later lookup return [AreaRepositoryError]; [givenWriteFails] makes every
 * later write return [AreaRepositoryError] and change nothing.
 */
class FakeAreasRepository : AreasRepository {
    private val store = linkedMapOf<UUID, Area>()
    private var readFails = false
    private var writeFails = false

    fun givenArea(vararg areas: Area) {
        areas.forEach { store[it.uuid] = it }
    }

    fun givenReadFails() {
        readFails = true
    }

    fun givenWriteFails() {
        writeFails = true
    }

    fun storedArea(areaId: UUID): Area? = store[areaId]

    fun storedAreas(): Collection<Area> = store.values.toList()

    override fun getAreaById(areaId: UUID): Either<AreaFetchFailure, Area> = when {
        readFails -> AreaRepositoryError.left()
        else -> store[areaId]?.right() ?: AreaNotFound(missingAreaId = areaId).left()
    }

    override fun findAreaByName(name: String): Either<AreaRepositoryError, Area?> =
        if (readFails) AreaRepositoryError.left() else store.values.find { it.name == name }.right()

    override fun save(area: Area): Either<AreaCreationFailure, Unit> = when {
        writeFails -> AreaRepositoryError.left()
        nameTakenByAnotherArea(area) -> AreaNameConflict.left()
        else -> store.set(area.uuid, area).right()
    }

    override fun getAll(): Either<GetAreasFailure, Collection<Area>> =
        if (readFails) AreaRepositoryError.left() else store.values.toList().right()

    override fun update(area: Area): Either<AreaUpdateFailure, Area> = when {
        writeFails -> AreaRepositoryError.left()
        area.uuid !in store -> AreaNotFound(missingAreaId = area.uuid).left()
        nameTakenByAnotherArea(area) -> AreaNameConflict.left()
        else -> area.also { store[it.uuid] = it }.right()
    }

    override fun deleteAreaById(areaId: UUID): Either<AreaDeletionFailure, Unit> = when {
        writeFails -> AreaRepositoryError.left()
        store.remove(areaId) == null -> AreaNotFound(missingAreaId = areaId).left()
        else -> Unit.right()
    }

    /** Mirrors the `UNIQUE (name)` constraint of the area table, which compares names case-sensitively. */
    private fun nameTakenByAnotherArea(area: Area) =
        store.values.any { it.name == area.name && it.uuid != area.uuid }
}
