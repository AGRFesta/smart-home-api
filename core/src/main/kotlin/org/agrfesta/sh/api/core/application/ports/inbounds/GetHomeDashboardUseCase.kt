package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.domain.failures.GetHomeDashboardFailure
import org.agrfesta.sh.api.core.domain.home.HomeDashboardDto

interface GetHomeDashboardUseCase {

    /**
     * Retrieves the full home dashboard data for the BFF `GET /home` endpoint.
     *
     * @return [Either.Right] containing the assembled [HomeDashboardDto], or [Either.Left] with a [GetHomeDashboardFailure]
     *         if the dashboard data could not be fetched.
     */
    fun execute(): Either<GetHomeDashboardFailure, HomeDashboardDto>
}
