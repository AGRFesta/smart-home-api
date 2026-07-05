package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.GetAreaByIdUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.readmodels.areas.AreaView
import org.agrfesta.sh.api.core.domain.failures.AreaFetchFailure
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetAreaByIdService(
    private val areasRepository: AreasRepository
) : GetAreaByIdUseCase {

    override fun execute(areaId: UUID): Either<AreaFetchFailure, AreaView> =
        areasRepository.getAreaById(areaId)
}
