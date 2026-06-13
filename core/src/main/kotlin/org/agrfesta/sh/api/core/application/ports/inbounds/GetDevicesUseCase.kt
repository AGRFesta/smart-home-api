package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.sh.api.core.domain.devices.DeviceFeature
import org.agrfesta.sh.api.core.domain.devices.DeviceStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.GetDevicesFailure

interface GetDevicesUseCase {

    /**
     * Retrieves the managed devices, optionally filtered.
     *
     * All filters are combinable with AND semantics; a `null` filter is not applied.
     *
     * @param provider when set, restricts the result to devices of this [Provider].
     * @param status when set, restricts the result to devices with this [DeviceStatus].
     * @param feature when set, restricts the result to devices exposing this [DeviceFeature].
     * @return [Either.Right] with the matching [Device] collection (possibly empty),
     *         or [Either.Left] with a [GetDevicesFailure] if a database error occurs.
     */
    fun execute(
        provider: Provider? = null,
        status: DeviceStatus? = null,
        feature: DeviceFeature? = null
    ): Either<GetDevicesFailure, Collection<Device>>
}
