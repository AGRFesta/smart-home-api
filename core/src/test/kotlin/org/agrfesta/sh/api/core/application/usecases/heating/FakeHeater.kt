package org.agrfesta.sh.api.core.application.usecases.heating

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.Heater
import org.agrfesta.sh.api.core.domain.devices.ActuatorStatus
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.failures.ActuatorOperationFailure
import org.agrfesta.test.mothers.aRandomUniqueString
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory [Heater] for state-based tests: [commands] lists, in order, every ON and OFF command it
 * accepted. [status] is what [getActuatorStatus] returns.
 */
class FakeHeater(
    override val uuid: UUID = UUID.randomUUID(),
    override val deviceProviderId: String = aRandomUniqueString(),
    override val provider: Provider = Provider.SWITCHBOT,
    var status: ActuatorStatus = ActuatorStatus.OFF
) : Heater {
    val commands: MutableList<HeaterCommandSent> = CopyOnWriteArrayList()

    override fun getActuatorStatus(): Either<ActuatorOperationFailure, ActuatorStatus> = status.right()

    /** Runs inside [on] before the command is recorded; a test blocks here to hold a command in flight. */
    var beforeOn: () -> Unit = {}

    override fun on(): Either<ActuatorOperationFailure, Unit> {
        beforeOn()
        commands.add(HeaterCommandSent.ON)
        return Unit.right()
    }

    override fun off(): Either<ActuatorOperationFailure, Unit> {
        commands.add(HeaterCommandSent.OFF)
        offException?.let { throw it }
        return offFailure?.left() ?: Unit.right()
    }

    /** Makes every later [off] throw [exception]; the command is still recorded in [commands]. */
    fun givenOffThrows(exception: Exception = IllegalStateException("driver error")) {
        offException = exception
    }

    private var offException: Exception? = null

    /** Makes every later [off] return [failure]; the command is still recorded in [commands]. */
    fun givenOffFails(failure: ActuatorOperationFailure = object : ActuatorOperationFailure {}) {
        offFailure = failure
    }

    private var offFailure: ActuatorOperationFailure? = null
}

enum class HeaterCommandSent { ON, OFF }
