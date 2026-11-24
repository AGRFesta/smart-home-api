package org.agrfesta.sh.api.core.application.usecases.heating

import arrow.core.Either
import arrow.core.left
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DeviceDriver
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.ProviderDevicesFactory
import org.agrfesta.sh.api.core.domain.failures.ActuatorFailureByException
import org.agrfesta.sh.api.core.domain.failures.ActuatorOperationFailure
import org.agrfesta.sh.api.core.domain.failures.DevicesUnavailable
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.slf4j.Logger
import org.springframework.stereotype.Service
import java.util.UUID

/** Builds a driver for every persisted device through the factory of its provider. */
@Service
class RegisteredDeviceDrivers(
    private val devicesRepository: DevicesRepository,
    providerDevicesFactories: Collection<ProviderDevicesFactory>
) {
    private val logger by LoggerDelegate()
    private val mappedDevicesFactories = providerDevicesFactories.associateBy { it.provider }

    /**
     * @return the drivers by device uuid; a device whose provider has no factory is left out.
     * [DevicesUnavailable] when the persisted devices cannot be read.
     */
    fun load(): Either<DevicesUnavailable, Map<UUID, DeviceDriver>> =
        devicesRepository.getAll()
            .mapLeft { DevicesUnavailable }
            .map { records ->
                records.mapNotNull { record ->
                    val factory = mappedDevicesFactories[record.provider]
                    if (factory == null) {
                        logger.error(
                            "No ProviderDevicesFactory registered for provider '${record.provider}', " +
                                "skipping device '${record.uuid}'"
                        )
                        null
                    } else {
                        factory.createDevice(record)
                    }
                }.associateBy { it.uuid }
            }
}

/** Runs a heater operation; an exception from the driver becomes a failure, so it cannot stop the caller. */
@Suppress("TooGenericExceptionCaught")
internal fun <T> callHeater(
    logger: Logger,
    operation: () -> Either<ActuatorOperationFailure, T>
): Either<ActuatorOperationFailure, T> =
    try {
        operation()
    } catch (e: Exception) {
        logger.error("Heater driver threw instead of returning a failure", e)
        ActuatorFailureByException(e).left()
    }
