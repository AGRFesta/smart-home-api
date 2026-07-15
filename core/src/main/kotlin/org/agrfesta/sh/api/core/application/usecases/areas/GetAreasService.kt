package org.agrfesta.sh.api.core.application.usecases.areas

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.areas.GetAreasUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.domain.areas.Area
import org.agrfesta.sh.api.core.domain.failures.GetAreasFailure
import org.springframework.stereotype.Service

@Service
class GetAreasService(
    private val areasRepository: AreasRepository
) : GetAreasUseCase {

    override fun execute(): Either<GetAreasFailure, Collection<Area>> =
        areasRepository.getAll()
}
