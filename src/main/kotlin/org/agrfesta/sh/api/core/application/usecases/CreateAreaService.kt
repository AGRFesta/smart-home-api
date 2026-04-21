package org.agrfesta.sh.api.core.application.usecases

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.inbounds.CreateAreaUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.AreasRepository
import org.agrfesta.sh.api.core.domain.areas.AreaDto
import org.agrfesta.sh.api.core.domain.failures.AreaCreationFailure
import org.agrfesta.sh.api.utils.RandomGenerator
import org.springframework.stereotype.Service

@Service
class CreateAreaService(
    private val areasRepository: AreasRepository,
    private val randomGenerator: RandomGenerator
) : CreateAreaUseCase {

    override fun execute(name: String, isIndoor: Boolean?): Either<AreaCreationFailure, AreaDto> {
        val area = AreaDto(
            uuid = randomGenerator.uuid(),
            name = name,
            isIndoor = isIndoor ?: true
        )
        return areasRepository.save(area).map { area }
    }

}
