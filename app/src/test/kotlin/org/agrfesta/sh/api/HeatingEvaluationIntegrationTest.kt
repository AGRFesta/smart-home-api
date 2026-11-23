package org.agrfesta.sh.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import org.agrfesta.sh.api.core.application.ports.inbounds.areas.AssignActuatorToAreaUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.areas.AssignSensorToAreaUseCase
import org.agrfesta.sh.api.core.application.ports.inbounds.heating.EvaluateHeatingStateUseCase
import org.agrfesta.sh.api.core.application.ports.outbounds.Cache
import org.agrfesta.sh.api.core.application.ports.outbounds.areas.AreasRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.devices.DevicesRepository
import org.agrfesta.sh.api.core.application.ports.outbounds.settings.TemperatureSettingsRepository
import org.agrfesta.sh.api.core.application.usecases.heating.EvaluateHeatingStateService.Companion.HEATING_ENABLED_KEY
import org.agrfesta.sh.api.core.application.usecases.heating.HeatingStrategySelector.Companion.HEATING_STRATEGY_KEY
import org.agrfesta.sh.api.core.domain.commons.Temperature
import org.agrfesta.sh.api.core.domain.devices.DeviceModel
import org.agrfesta.sh.api.core.domain.devices.Provider
import org.agrfesta.sh.api.core.domain.heating.ActuationOutcome
import org.agrfesta.sh.api.core.domain.heating.HeaterActionOutcome
import org.agrfesta.sh.api.core.domain.heating.HeaterCommand
import org.agrfesta.sh.api.core.domain.heating.HeatingEvaluationReport
import org.agrfesta.sh.api.domain.aSensorProviderData
import org.agrfesta.sh.api.domain.anActuatorProviderData
import org.agrfesta.sh.api.domain.anArea
import org.agrfesta.sh.api.domain.anAreaTemperatureSetting
import org.agrfesta.sh.api.persistence.jdbc.repositories.PropertyJdbcRepository
import org.agrfesta.sh.api.providers.netatmo.KtorRequestFailure
import org.agrfesta.sh.api.providers.netatmo.NetatmoClientAsserter
import org.agrfesta.sh.api.providers.netatmo.NetatmoService.Companion.NETATMO_ACCESS_TOKEN_CACHE_KEY
import org.agrfesta.sh.api.providers.netatmo.aNetatmoHomeStatus
import org.agrfesta.test.mothers.aJsonNode
import org.agrfesta.test.mothers.aRandomUniqueString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class HeatingEvaluationIntegrationTest(
    private val evaluateHeatingState: EvaluateHeatingStateUseCase,
    private val areasRepository: AreasRepository,
    private val devicesRepository: DevicesRepository,
    private val assignSensorToArea: AssignSensorToAreaUseCase,
    private val assignActuatorToArea: AssignActuatorToAreaUseCase,
    private val temperatureSettingsRepository: TemperatureSettingsRepository,
    private val propertyRepository: PropertyJdbcRepository,
    private val netatmoClientAsserter: NetatmoClientAsserter,
    private val cache: Cache,
    private val objectMapper: ObjectMapper
) : AbstractIntegrationTest() {

    @BeforeEach
    fun setUpNetatmo() {
        netatmoClientAsserter.clear()
        cache.set(NETATMO_ACCESS_TOKEN_CACHE_KEY, aRandomUniqueString())
    }

    @Test fun `execute() commands every heater when Netatmo answers 503 to the OFF command of one Smarther`() {
        // Given
        propertyRepository.upsert(HEATING_ENABLED_KEY, "true")
        propertyRepository.upsert(HEATING_STRATEGY_KEY, "COMFORT")
        val livingRoom = anArea()
        areasRepository.save(livingRoom)
        temperatureSettingsRepository.createSetting(
            anAreaTemperatureSetting(areaId = livingRoom.uuid, defaultTemperature = Temperature.of("21"))
        )
        val meterData = aSensorProviderData(model = DeviceModel("switchbot/Meter"))
        val meterId = randomGenerator.uuid()
        devicesRepository.create(meterId, meterData).shouldBeRight()
        assignSensorToArea.execute(livingRoom.uuid, meterId).shouldBeRight()
        coEvery { switchBotDevicesClient.getDeviceStatus(meterData.deviceProviderId) } returns
            objectMapper.readTree("""{"body":{"temperature":18.0,"humidity":50}}""")
        val h1 = randomGenerator.uuid()
        devicesRepository.create(h1, aSmartherData()).shouldBeRight()
        assignActuatorToArea.execute(livingRoom.uuid, h1).shouldBeRight()
        val h2 = randomGenerator.uuid()
        devicesRepository.create(h2, aSmartherData()).shouldBeRight()
        netatmoClientAsserter.givenHomeStatusFetchResponse(aNetatmoHomeStatus())
        // The Smarther OFF command sets the room setpoint to 7°: only H2, which has no heatable area, receives it.
        netatmoClientAsserter.givenSetStatusFailureFor(BigDecimal("7"), HttpStatusCode.ServiceUnavailable)
        netatmoClientAsserter.givenSetStatusResponse(aJsonNode())

        // When
        val result = evaluateHeatingState.execute()

        // Then
        val outcomes = result.shouldBeRight().shouldBeInstanceOf<HeatingEvaluationReport.Evaluated>()
            .heaters.associateBy { it.heaterId }
        withClue("H1 should receive the ON command decided for the living room") {
            outcomes[h1] shouldBe HeaterActionOutcome(h1, HeaterCommand.ON, ActuationOutcome.Issued)
        }
        withClue("H2 should record its OFF command as failed with the 503 of Netatmo") {
            val h2Outcome = outcomes[h2].shouldNotBeNull()
            h2Outcome.command shouldBe HeaterCommand.OFF
            h2Outcome.outcome.shouldBeInstanceOf<ActuationOutcome.Failed>()
                .cause.shouldBeInstanceOf<KtorRequestFailure>()
                .failureStatusCode shouldBe HttpStatusCode.ServiceUnavailable
        }
    }

    private fun aSmartherData() =
        anActuatorProviderData(provider = Provider.NETATMO, model = DeviceModel("netatmo/Smarther"))
}
