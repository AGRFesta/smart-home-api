package org.agrfesta.sh.api.schedulers

import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.agrfesta.sh.api.domain.areas.HeatableArea
import org.agrfesta.sh.api.domain.commons.Temperature
import org.agrfesta.sh.api.domain.devices.Heater
import org.agrfesta.sh.api.domain.failures.KtorRequestFailure
import org.agrfesta.sh.api.services.AreasService
import org.agrfesta.sh.api.services.DevicesService
import org.agrfesta.sh.api.utils.LoggerDelegate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class HeatingControlScheduler(
    private val devicesService: DevicesService,
    private val areasService: AreasService
) {
    private val logger by LoggerDelegate()

    @Scheduled(cron = "0 */15 * * * *")
    @Async
    fun scheduledTask() {
        logger.info("[SCHEDULED TASK] start heating control...")
        devicesService.getAllDevices().onRight {
            val heathers = it.filterIsInstance<Heater>()
            logger.info("Found ${heathers.size} Heaters")
            val devicesRegistry = it.associateBy { d -> d.uuid }
            areasService.getAllAreas(devicesRegistry).filterIsInstance<HeatableArea>()
                .also { areas -> logger.info("Found ${areas.size} HeatableArea") }
                .groupBy { a -> a.heater }
                .forEach { (heater, area) -> handleHeater(heater, area) }
        }
            .onLeft { error("devices fetch failure") }


        //  - Recupera tutti gli shared warmers
        //  - Per ogni shared warmer:
        //      - Recupera tutte le aree collegate:
        //          - con una richiesta di temperatura (attivo un warming schedule)
        //          - con temperatura richiesta superiore a quella attuale
        //      - Per ogni area considera la temperatura richiesta:
        //
        logger.info("[SCHEDULED TASK] end heating control")
    }

    private fun handleHeater(heater: Heater, areas: Collection<HeatableArea>) {
        logger.info("Handling heater ${heater.uuid}...")
        findAreaWithMaxTempDiffPairConsideringHysteresis(areas)?.apply {
            runBlocking {
                if (second.signum() < 0) {
                    logger.info("Turning heater ${heater.uuid} ON")
                    heater.on()
                        .onLeft {
                            failure -> when (failure) {
                                is KtorRequestFailure -> { logger.error("ON Failure: ${failure.body}") }
                                else -> { logger.error("ON unexpected failure") }
                            }
                        }
                } else {
                    logger.info("Turning heater ${heater.uuid} OFF")
                    heater.off()
                        .onLeft {
                                failure -> when (failure) {
                            is KtorRequestFailure -> { logger.error("OFF Failure: ${failure.body}") }
                            else -> { logger.error("OFF unexpected failure") }
                        }
                        }
                }
            }
        }
//        val greaterTempDelta = areas
//            .mapNotNull { obj ->
//                val target = obj.getActualTargetTemperature()
//                val actual = obj.getActualTemperature().fold(
//                    { null },  // se Failure, ignora
//                    { it }     // se Success, restituisce la temperatura
//                )
//                if (target != null && actual != null) {
//                    val diff = actual.subtract(target)
//                    Triple(obj, diff, diff.abs())
//                } else {
//                    null
//                }
//            }
//            .maxByOrNull { it.third }  // seleziona quello con differenza assoluta maggiore
//            ?.let { Pair(it.first, it.second) } // ritorna Pair<Oggetto, differenza con segno>

    }

    /**
     * Same as above but returns a simple Pair<T, BigDecimal>,
     * keeping only the object and the signed difference.
     */
    fun findAreaWithMaxTempDiffPairConsideringHysteresis(
        area: Collection<HeatableArea>,
        hysteresis: BigDecimal = BigDecimal.ONE
    ): Pair<HeatableArea, BigDecimal>? {
        return findObjectWithMaxTempDiffConsideringHysteresis(area, hysteresis)
            ?.let { Pair(it.obj, it.diff) }
    }

    /**
     * Finds the object with the largest absolute temperature difference (actual - target),
     * considering only those whose |diff| >= hysteresis.
     *
     * Returns TempDiffResult<T>? (null if no object exceeds hysteresis).
     */
    fun findObjectWithMaxTempDiffConsideringHysteresis(
        areas: Collection<HeatableArea>,
        hysteresis: BigDecimal = BigDecimal.ONE
    ): TempDiffResult<HeatableArea>? {
        return areas
            .mapNotNull { area ->
                val (actual, target) = extractTemperaturesNullable(area)
                if (actual != null && target != null) {
                    val diff = actual.subtract(target) // actual - target, keeps sign
                    val abs = diff.abs()
                    if (abs >= hysteresis) {
                        TempDiffResult(
                            obj = area,
                            diff = diff,
                            absDiff = abs,
                            shouldHeat = shouldHeatForDiff(diff, hysteresis)
                        )
                    } else {
                        logger.info("in ${area.uuid} below hysteresis threshold")
                        null // below hysteresis threshold
                    }
                } else {
                    logger.info("in ${area.uuid} missing temperature values")
                    null // missing temperature values
                }
            }
            .maxByOrNull { it.absDiff } // select the one with the largest absolute difference
    }

    /**
     * Determines whether heating should be turned on, given diff = actual - target.
     *
     * Logic:
     *  - if diff < 0 and |diff| >= hysteresis -> heating ON
     *  - otherwise -> heating OFF
     *
     * @param diff actual - target temperature
     * @param hysteresis the hysteresis threshold (default = 1°C)
     */
    fun shouldHeatForDiff(diff: BigDecimal, hysteresis: BigDecimal = BigDecimal.ONE): Boolean {
        return diff.signum() < 0 && diff.abs() >= hysteresis
    }

    // Helper function to extract nullable temperatures
    private fun extractTemperaturesNullable(area: HeatableArea): Pair<Temperature?, Temperature?> {
        // obj.getActualTargetTemperature(): Temperature?
        // obj.getActualTemperature(): Either<Failure, Temperature>
        val target: Temperature? = area.getActualTargetTemperature()
        val actual: Temperature? = area.getActualTemperature().fold(
            { null },
            { it }
        )
        return Pair(actual, target)
    }

}

// Result data class with all useful information
data class TempDiffResult<T>(
    val obj: T,
    val diff: BigDecimal,         // actual - target (with sign)
    val absDiff: BigDecimal,      // absolute difference
    val shouldHeat: Boolean       // true if heating should be turned on according to hysteresis
)