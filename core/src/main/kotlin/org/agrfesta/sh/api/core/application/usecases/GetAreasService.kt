package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.GetAreasUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.failures.GetAreasFailure
import org.springframework.stereotype.Service

@Service
class GetAreasService(
    private val areasRepository: AreasRepository
) : GetAreasUseCase {

    override fun execute(): Either<GetAreasFailure, Collection<AreaDto>> =
        areasRepository.getAll()
}
