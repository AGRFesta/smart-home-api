package org.agrfesta.sh.api.services

import arrow.core.Either
import java.util.*
import org.agrfesta.sh.api.domain.areas.Area
import org.agrfesta.sh.api.domain.areas.AreaDto
import org.agrfesta.sh.api.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.domain.areas.AreasFactory
import org.agrfesta.sh.api.domain.devices.Device
import org.agrfesta.sh.api.domain.failures.AreaCreationFailure
import org.agrfesta.sh.api.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.persistence.AreasDao
import org.agrfesta.sh.api.persistence.AreasWithDevicesDao
import org.agrfesta.sh.api.utils.RandomGenerator
import org.springframework.stereotype.Service

/**
 * Service responsible for managing areas within the smart home system.
 *
 * Provides operations to create areas and retrieve them, optionally enriched with
 * the devices associated to each area.
 */
@Service
class AreasService(
    private val areasDao: AreasDao,
    private val areasWithDevicesDao: AreasWithDevicesDao,
    private val randomGenerator: RandomGenerator,
    private val areasFactory: AreasFactory
) {

    /**
     * Creates a new area with the given [name] and optional [isIndoor] flag.
     *
     * A fresh UUID is generated for the area. When [isIndoor] is not provided it defaults to `true`.
     *
     * @param name the display name of the area to create.
     * @param isIndoor whether the area is indoors; defaults to `true` when `null`.
     * @return [Either.Right] containing the persisted [AreaDto], or [Either.Left] with an [AreaCreationFailure]
     *         if the area could not be saved (e.g. a duplicate name conflict).
     */
    fun createArea(name: String, isIndoor: Boolean? = null): Either<AreaCreationFailure, AreaDto> {
        val area = AreaDto(
            uuid = randomGenerator.uuid(),
            name = name,
            isIndoor = isIndoor ?: true
        )
        return areasDao.save(area).map { area }
    }

    /**
     * Retrieves all areas together with the raw list of devices associated to each one.
     *
     * @return [Either.Right] containing a collection of [AreaDtoWithDevices], or [Either.Left] with a
     *         [PersistenceFailure] if the query fails.
     */
    fun getAllAreasWithDevices(): Either<PersistenceFailure, Collection<AreaDtoWithDevices>> =
        areasWithDevicesDao.getAllAreasWithDevices()

    /**
     * Retrieves all areas as fully assembled domain objects, resolving each area's devices from [devicesRegistry].
     *
     * @param devicesRegistry a map from device UUID to the corresponding [Device] domain object used to
     *        hydrate device references inside each area.
     * @return [Either.Right] containing a collection of domain [Area] objects, or [Either.Left] with a
     *         [PersistenceFailure] if the underlying query fails.
     */
    fun getAllAreas(devicesRegistry: Map<UUID, Device>): Either<PersistenceFailure, Collection<Area>> =
        getAllAreasWithDevices().map { areas -> areas.map { areasFactory.createArea(it, devicesRegistry) } }

}