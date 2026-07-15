package org.agrfesta.sh.api.core.application.usecases.areas

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.areas.CreateAreaUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.RandomGenerator
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.domain.areas.Area
import org.agrfesta.sh.api.core.domain.failures.AreaCreationFailure
import org.springframework.stereotype.Service

@Service
class CreateAreaService(
    private val areasRepository: AreasRepository,
    private val randomGenerator: RandomGenerator
) : CreateAreaUseCase {

    override fun execute(name: String, isIndoor: Boolean?): Either<AreaCreationFailure, Area> {
        val area = Area(
            uuid = randomGenerator.uuid(),
            name = name,
            isIndoor = isIndoor ?: true
        )
        return areasRepository.save(area).map { area }
    }
}
