package org.agrfesta.sh.api.core.application.ports.outbounds.areas

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.core.domain.failures.PersistenceFailure

interface AreasWithDevicesRepository {

    /**
     * Fetches all areas and related devices.
     *
     * @return [Either.Right] with a collection of [AreaDtoWithDevices],
     * or [Either.Left] with [PersistenceFailure] if a database error occurs.
     */
    fun getAllAreasWithDevices(): Either<PersistenceFailure, Collection<AreaDtoWithDevices>>

}
