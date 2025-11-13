package org.agrfesta.sh.api.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.*
import org.agrfesta.sh.api.domain.areas.Area
import org.agrfesta.sh.api.domain.areas.AreaDto
import org.agrfesta.sh.api.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.domain.areas.AreasFactory
import org.agrfesta.sh.api.domain.devices.Device
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
    private val randomGenerator: RandomGenerator,
    private val areasFactory: AreasFactory
) {

    fun createArea(name: String, isIndoor: Boolean? = null): Either<AreaCreationFailure, AreaDto> {
        val area = AreaDto(
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
    fun getAllAreasWithDevices(): Collection<AreaDtoWithDevices> { //TODO return monad
        return areasWithDevicesDao.getAllAreasWithDevices()
    }

    fun getAllAreas(devicesRegistry: Map<UUID, Device>): Collection<Area> {
        return getAllAreasWithDevices().map { areasFactory.createArea(it, devicesRegistry) }
    }

}