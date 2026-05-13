package org.agrfesta.sh.api.core.application.ports.outbounds

import arrow.core.Either

/**
 * Defines a boundary within which all operations are treated as a single atomic unit of work.
 *
 * Based on Martin Fowler's Unit of Work pattern: the [execute] block groups one or more
 * operations that must either all succeed or all be undone together, preserving consistency.
 *
 * A [arrow.core.Either.Right] result signals that the unit of work completed successfully and all changes
 * should be made permanent. A [arrow.core.Either.Left] result signals a failure: all changes performed
 * inside the block must be discarded.
 */
interface UnitOfWork {

    /**
     * Executes [block] as a single atomic unit of work.
     *
     * @param E the error type returned on failure.
     * @param A the success type returned on success.
     * @param block the operations to run as a single unit.
     * @return [arrow.core.Either.Right] with the result if the unit of work succeeded, or
     *         [Either.Left] with the failure if it did not — in which case all changes are discarded.
     */
    fun <E, A> execute(block: () -> Either<E, A>): Either<E, A>
}