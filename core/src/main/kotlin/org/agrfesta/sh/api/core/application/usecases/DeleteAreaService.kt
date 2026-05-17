package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.DeleteAreaUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.domain.failures.AreaDeletionFailure
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DeleteAreaService(
    private val areasRepository: AreasRepository
) : DeleteAreaUseCase {

    override fun execute(areaId: UUID): Either<AreaDeletionFailure, Unit> =
        areasRepository.deleteAreaById(areaId)
}
