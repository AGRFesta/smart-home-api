package org.agrfesta.sh.api.core.application.ports.outbounds.areas

import arrow.core.Either
import org.agrfesta.sh.api.core.application.readmodels.areas.AreaWithDevicesView
import org.agrfesta.sh.api.core.domain.failures.AreaRepositoryError

/**
 * Read port: intentionally returns the [AreaWithDevicesView] read-model instead of a domain
 * aggregate. Current consumers: the home dashboard query and the heating evaluation
 * snapshot building in `EvaluateHeatingStateService`.
 */
interface AreasWithDevicesRepository {

    /**
     * Fetches all areas and related devices.
     *
     * @return [Either.Right] with a collection of [AreaWithDevicesView],
     * or [Either.Left] with [AreaRepositoryError] if a database error occurs.
     */
    fun getAllAreasWithDevices(): Either<AreaRepositoryError, Collection<AreaWithDevicesView>>
}
