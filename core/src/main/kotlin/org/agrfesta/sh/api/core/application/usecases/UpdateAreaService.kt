package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.UpdateAreaUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.failures.AreaUpdateFailure
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UpdateAreaService(
    private val areasRepository: AreasRepository
) : UpdateAreaUseCase {

    override fun execute(areaId: UUID, name: String, isIndoor: Boolean): Either<AreaUpdateFailure, AreaDto> =
        areasRepository.update(AreaDto(uuid = areaId, name = name, isIndoor = isIndoor))
}
