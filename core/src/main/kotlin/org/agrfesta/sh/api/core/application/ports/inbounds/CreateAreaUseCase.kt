package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.failures.AreaCreationFailure

interface CreateAreaUseCase {

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
    fun execute(name: String, isIndoor: Boolean? = null): Either<AreaCreationFailure, AreaDto>
}
