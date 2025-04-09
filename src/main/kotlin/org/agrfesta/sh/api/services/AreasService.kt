package org.agrfesta.sh.api.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.domain.Area
import org.agrfesta.sh.api.domain.AreaWithDevices
import org.agrfesta.sh.api.domain.failures.AreaCreationFailure
import org.agrfesta.sh.api.domain.failures.AreaNameConflict
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.AreaDao
import org.agrfesta.sh.api.persistence.AreasWithDevicesDao
import org.agrfesta.sh.api.persistence.SameNameAreaException
import org.agrfesta.sh.api.utils.RandomGenerator
import org.springframework.stereotype.Service

@Service
class AreasService(
    private val areasDao: AreaDao,
    private val areasWithDevicesDao: AreasWithDevicesDao,
    private val randomGenerator: RandomGenerator
) {

    fun createArea(name: String, isIndoor: Boolean? = null): Either<AreaCreationFailure, Area> {
        val area = Area(
            uuid = randomGenerator.uuid(),
            name = name,
            isIndoor = isIndoor ?: true
        )
        return try {
            areasDao.save(area)
            area.right()
        } catch (e: SameNameAreaException) {
            AreaNameConflict.left()
        } catch (e: Exception) {
            PersistenceFailure(e).left()
        }
    }

    /**
     * Fetches all areas and related devices.
     */
    fun getAllAreasWithDevices(): Collection<AreaWithDevices> { //TODO return monad
        return areasWithDevicesDao.getAllAreasWithDevices()
    }

}