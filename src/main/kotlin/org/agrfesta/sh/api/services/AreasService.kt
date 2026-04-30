package org.agrfesta.sh.api.services

import arrow.core.Either
import java.util.*
import org.agrfesta.sh.api.core.domain.areas.Area
import org.agrfesta.sh.api.core.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.core.domain.areas.AreasFactory
import org.agrfesta.sh.api.core.domain.devices.DeviceDriver
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasWithDevicesRepository
import org.springframework.stereotype.Service

/**
 * Service responsible for retrieving areas within the smart home system.
 *
 * Provides operations to retrieve areas, optionally enriched with
 * the devices associated to each area.
 */
@Service
class AreasService(
    private val areasWithDevicesRepository: AreasWithDevicesRepository,
    private val areasFactory: AreasFactory
) {

    /**
     * Retrieves all areas together with the raw list of devices associated to each one.
     *
     * @return [Either.Right] containing a collection of [AreaDtoWithDevices], or [Either.Left] with a
     *         [PersistenceFailure] if the query fails.
     */
    fun getAllAreasWithDevices(): Either<PersistenceFailure, Collection<AreaDtoWithDevices>> =
        areasWithDevicesRepository.getAllAreasWithDevices()

    /**
     * Retrieves all areas as fully assembled domain objects, resolving each area's devices from [devicesRegistry].
     *
     * @param devicesRegistry a map from device UUID to the corresponding [DeviceDriver] used to
     *        hydrate device references inside each area.
     * @return [Either.Right] containing a collection of domain [Area] objects, or [Either.Left] with a
     *         [PersistenceFailure] if the underlying query fails.
     */
    fun getAllAreas(devicesRegistry: Map<UUID, DeviceDriver>): Either<PersistenceFailure, Collection<Area>> =
        getAllAreasWithDevices().map { areas -> areas.map { areasFactory.createArea(it, devicesRegistry) } }

}