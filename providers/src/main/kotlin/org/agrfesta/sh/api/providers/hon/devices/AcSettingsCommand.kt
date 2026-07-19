package org.agrfesta.sh.api.providers.hon.devices

import arrow.core.Either
import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal

/**
 * The hOn AC `settings` command as a read-modify-write value object.
 *
 * hOn's `settings` command always transmits ALL of its parameters, so a write is never a
 * single-field update: the full parameter set is rebuilt from the device command catalog
 * (`GET /commands/v1/retrieve`, `settings.setParameters`) overlaid with the latest device
 * state (`GET /commands/v1/context`), and the requested change is validated against the
 * catalog rule of its parameter (enum membership, range/step). Ported from the addhOn
 * engine semantics (`HonCommand` + `ac_command.py`).
 */
class AcSettingsCommand private constructor(
    private val catalog: JsonNode,
    private val context: JsonNode,
    private val changes: Map<String, String>,
) {
    constructor(catalog: JsonNode, context: JsonNode) : this(catalog, context, emptyMap())

    /**
     * Returns a copy carrying the requested change, validated against the parameter's
     * catalog rule (enum membership, range/step). The change wins over the current state.
     */
    fun with(parameter: String, value: String): Either<HonAcFailure, AcSettingsCommand> =
        when (val failure = validationFailureOf(parameter, value)) {
            null -> Either.Right(AcSettingsCommand(catalog, context, changes + (parameter to value)))
            else -> Either.Left(failure)
        }

    /** The catalog rule the change breaks, or null when it is admitted. */
    private fun validationFailureOf(parameter: String, value: String): HonAcFailure? {
        val spec = catalog.at("/settings/setParameters/parameters/$parameter")
        val allowed = spec.enumValues()
        return when {
            spec.isMissingNode -> AcUnknownParameter(parameter)
            allowed != null && value !in allowed -> AcSettingNotSupported(parameter, value, allowed)
            spec.get("typology")?.asText() == "range" && !spec.admitsInRange(value) ->
                AcSettingOutOfRange(parameter, value)
            else -> null
        }
    }

    /**
     * The admitted enum values as strings (the cloud sends them as JSON numbers), or null.
     * An EMPTY `enumValues` is catalog drift, not a rule: null too — otherwise it would
     * reject every value and crash the sanitization fallbacks.
     */
    private fun JsonNode.enumValues(): List<String>? =
        get("enumValues")?.takeIf { it.isArray }?.map { it.asText() }?.takeIf { it.isNotEmpty() }

    /**
     * True when [value] falls inside this range spec's `minimumValue..maximumValue` AND lands
     * on the `minimumValue + n * incrementValue` grid (exact [BigDecimal] arithmetic — no
     * float epsilon needed). A missing/zero increment means "no declared grid" (addhOn quirk).
     * Missing or non-numeric BOUNDS are catalog drift: the rule is unenforceable, so the
     * value is admitted rather than bricking the control — the cloud stays the ultimate
     * validator (a bad value comes back as a rejected command, still a typed failure).
     */
    private fun JsonNode.admitsInRange(value: String): Boolean {
        val requested = value.toBigDecimalOrNull() ?: return false
        val min = get("minimumValue")?.asText()?.toBigDecimalOrNull()
        val max = get("maximumValue")?.asText()?.toBigDecimalOrNull()
        if (min == null || max == null) return true
        val step = get("incrementValue")?.asText()?.toBigDecimalOrNull()
        val onGrid = step == null || step.signum() <= 0 ||
            (requested - min).remainder(step).signum() == 0
        return requested in min..max && onGrid
    }

    /** The full `parameters` block of the send body: one entry per catalog parameter. */
    fun parameters(): Map<String, String> = buildMap {
        catalog.at("/settings/setParameters/parameters").fields().forEach { (name, spec) ->
            val base = sanitizedWind(name, contextValueOf(name, spec) ?: spec.defaultOf(), spec)
            put(name, changes[name] ?: base)
        }
    }

    /**
     * Resets an off-enum wind direction to an admitted value: the device reports `"0"` while
     * off, the cloud rejects the whole command on it (port of addhOn's
     * `sanitize_wind_direction`). Vertical prefers the FIXED position `"2"` (never the swing
     * `"8"`), horizontal the first non-`"0"`. Applied to the device-reported base only — a
     * requested change wins untouched. No enum info in the catalog → nothing to sanitize
     * against, value passes through.
     */
    private fun sanitizedWind(name: String, value: String, spec: JsonNode): String {
        if (name !in WIND_DIRECTION_PARAMETERS) return value
        val allowed = spec.enumValues() ?: return value
        // Vertical prefers the fixed position 2 among the non-swing values; never 8 (swing).
        val fixedVertical = (allowed - WIND_SWING_VERTICAL).let { fixed ->
            when {
                WIND_FIXED_VERTICAL in fixed -> WIND_FIXED_VERTICAL
                else -> fixed.firstOrNull() ?: WIND_SWING_VERTICAL
            }
        }
        return when {
            value in allowed -> value
            name == WIND_DIRECTION_VERTICAL -> fixedVertical
            else -> allowed.firstOrNull { it != "0" } ?: allowed.first()
        }
    }

    /**
     * The `ancillaryParameters` block of the send body. `category == "rule"` entries
     * (programRules) are engine metadata whose `fixedValue` is an object, not a wire value:
     * addhOn strips them before sending, and so do we.
     */
    fun ancillaryParameters(): Map<String, String> = buildMap {
        catalog.at("/settings/setParameters/ancillaryParameters").fields().forEach { (name, spec) ->
            if (spec.get("category")?.asText() != "rule") {
                put(name, contextValueOf(name, spec) ?: spec.defaultOf())
            }
        }
    }

    /** Latest device state of a parameter (`shadow.parameters.<name>.parNewVal`), if reported. */
    private fun contextValueOf(name: String, spec: JsonNode): String? {
        val reported = context.at("/shadow/parameters/$name/parNewVal")
            .takeUnless { it.isMissingNode }?.asText() ?: return null
        return if (spec.get("typology")?.asText() == "range") reported.normalizedDecimal() else reported
    }

    /**
     * Strips insignificant decimals from a range value: the context reports `"26.00"` but the
     * catalog grid is declared in plain form (`"16".."30"` step `"1"`) — mirror of addhOn's
     * `str_to_float` normalization. Non-numeric values pass through untouched.
     */
    private fun String.normalizedDecimal(): String =
        runCatching { BigDecimal(this).stripTrailingZeros().toPlainString() }.getOrDefault(this)

    /**
     * Catalog default of a parameter: `fixedValue` for fixed typology, `defaultValue`
     * otherwise — falling back to `"0"` when either is absent (an enum may omit it, e.g. the
     * real `windDirectionVerticalPositionSequence`), like addhOn's base parameter does.
     */
    private fun JsonNode.defaultOf(): String =
        if (get("typology")?.asText() == "fixed") {
            get("fixedValue")?.asText() ?: "0"
        } else {
            get("defaultValue")?.asText() ?: "0"
        }

    companion object {
        private const val WIND_DIRECTION_VERTICAL = "windDirectionVertical"
        private val WIND_DIRECTION_PARAMETERS = setOf(WIND_DIRECTION_VERTICAL, "windDirectionHorizontal")

        /** Vertical position 8 = swing (oscillation): never a valid "fixed" fallback. */
        private const val WIND_SWING_VERTICAL = "8"

        /** The preferred fixed vertical position when resetting an off-enum value. */
        private const val WIND_FIXED_VERTICAL = "2"
    }
}
