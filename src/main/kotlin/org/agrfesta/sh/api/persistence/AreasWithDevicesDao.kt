package org.agrfesta.sh.api.persistence

import arrow.core.Either
import org.agrfesta.sh.api.domain.areas.AreaDtoWithDevices
import org.agrfesta.sh.api.domain.failures.PersistenceFailure

interface AreasWithDevicesDao {

    /**
     * Fetches all areas and related devices.
     *
     * @return [Either.Right] with a collection of [AreaDtoWithDevices],
     * or [Either.Left] with [PersistenceFailure] if a database error occurs.
     */
    fun getAllAreasWithDevices(): Either<PersistenceFailure, Collection<AreaDtoWithDevices>>

}
