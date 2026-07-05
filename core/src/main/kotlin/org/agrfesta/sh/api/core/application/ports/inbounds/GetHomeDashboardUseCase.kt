package org.agrfesta.sh.api.core.application.ports.inbounds

import arrow.core.Either
import org.agrfesta.sh.api.core.application.readmodels.home.HomeDashboardView
import org.agrfesta.sh.api.core.domain.failures.GetHomeDashboardFailure

interface GetHomeDashboardUseCase {

    /**
     * Retrieves the full home dashboard data for the BFF `GET /home` endpoint.
     *
     * @return [Either.Right] containing the assembled [HomeDashboardView], or [Either.Left] with a [GetHomeDashboardFailure]
     *         if the dashboard data could not be fetched.
     */
    fun execute(): Either<GetHomeDashboardFailure, HomeDashboardView>
}
