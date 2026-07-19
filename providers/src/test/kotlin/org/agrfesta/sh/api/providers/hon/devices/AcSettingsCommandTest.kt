package org.agrfesta.sh.api.providers.hon.devices

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.agrfesta.sh.api.providers.hon.HON_OBJECT_MAPPER
import org.junit.jupiter.api.Test

class AcSettingsCommandTest {
    private val mapper = HON_OBJECT_MAPPER

    /**
     * Minimal catalog mirroring the REAL AS35PBPHRA-PRE `settings.setParameters` shape
     * (live capture 2026-07-18, full version in resources/hon/ac-commands.json): one
     * parameter per typology plus the enums the write path drives. Note the enumValues
     * as JSON NUMBERS: that is how the cloud sends them.
     */
    private val catalog = mapper.readTree(
        """
        {
          "settings": {
            "setParameters": {
              "parameters": {
                "onOffStatus": {"category": "command", "typology": "fixed", "mandatory": 1, "fixedValue": "1"},
                "machMode": {
                  "category": "command", "typology": "enum", "mandatory": 1,
                  "defaultValue": "0", "enumValues": [0, 1, 2, 4, 6]
                },
                "tempSel": {
                  "category": "command", "typology": "range", "mandatory": 1,
                  "defaultValue": "22", "minimumValue": "16", "maximumValue": "30", "incrementValue": "1"
                },
                "windSpeed": {
                  "category": "command", "typology": "enum", "mandatory": 1,
                  "defaultValue": "5", "enumValues": [1, 2, 3, 5]
                },
                "windDirectionVertical": {
                  "category": "command", "typology": "enum", "mandatory": 1,
                  "defaultValue": "5", "enumValues": [2, 4, 5, 6, 7, 8]
                },
                "windDirectionHorizontal": {
                  "category": "command", "typology": "enum", "mandatory": 1,
                  "defaultValue": "0", "enumValues": [0, 3, 4, 5, 6, 7]
                }
              },
              "ancillaryParameters": {
                "programRules": {
                  "category": "rule", "typology": "fixed", "mandatory": 0,
                  "fixedValue": {"selfCleaningStatus": {"${'$'}installationType": {"1toN": {"typology": "fixed", "fixedValue": "0"}}}}
                },
                "remoteActionable": {"category": "general", "typology": "fixed", "mandatory": 0, "fixedValue": "1"},
                "remoteVisible": {"category": "general", "typology": "fixed", "mandatory": 0, "fixedValue": "1"},
                "windDirectionVerticalPositionSequence": {
                  "category": "general", "typology": "enum", "mandatory": 0, "enumValues": [2, 4, 5, 6, 7, 8]
                }
              }
            }
          }
        }
        """,
    )
    private val emptyContext = mapper.readTree("""{"shadow":{"parameters":{}}}""")

    @Test
    fun `parameters resolves from the context state when present, normalizing range decimals`() {
        // Given: the REAL context shape — range values carry two decimals ("26.00") that the
        // step-1 catalog grid does not admit: they must be normalized ("26"), addhOn-style.
        val context = mapper.readTree(
            """
            {"shadow": {"parameters": {
              "machMode": {"parNewVal": "1", "lastUpdate": "2026-07-18T14:06:57Z"},
              "tempSel": {"parNewVal": "26.00", "lastUpdate": "2026-07-18T21:13:39Z"}
            }}}
            """,
        )
        val command = AcSettingsCommand(catalog, context)

        // When
        val parameters = command.parameters()

        // Then
        withClue("context values must override the catalog defaults") {
            parameters["machMode"] shouldBe "1"
        }
        withClue("range values from the context must be normalized to the catalog grid form") {
            parameters["tempSel"] shouldBe "26"
        }
    }

    @Test
    fun `with overlays a valid enum change over the current state`() {
        // Given: the device is currently cooling (machMode 1)
        val context = mapper.readTree(
            """{"shadow": {"parameters": {"machMode": {"parNewVal": "1"}}}}""",
        )
        val command = AcSettingsCommand(catalog, context)

        // When: heating is requested (machMode 4, admitted by the enum [0,1,2,4,6])
        val result = command.with("machMode", "4")

        // Then
        val changed = result.shouldBeRight()
        withClue("the requested change must win over the current context state") {
            changed.parameters()["machMode"] shouldBe "4"
        }
    }

    @Test
    fun `with rejects an enum value the firmware does not admit`() {
        // Given: machMode admits [0, 1, 2, 4, 6] — "3" is a hole in the enum
        val command = AcSettingsCommand(catalog, emptyContext)

        // When
        val result = command.with("machMode", "3")

        // Then
        val failure = result.shouldBeLeft().shouldBeInstanceOf<AcSettingNotSupported>()
        withClue("the failure must carry the parameter, the rejected value and the admitted set") {
            failure.parameter shouldBe "machMode"
            failure.value shouldBe "3"
            failure.allowed shouldBe listOf("0", "1", "2", "4", "6")
        }
    }

    @Test
    fun `with rejects a range value above the maximum`() {
        // Given: tempSel admits 16..30 step 1
        val command = AcSettingsCommand(catalog, emptyContext)

        // When
        val result = command.with("tempSel", "31")

        // Then
        val failure = result.shouldBeLeft().shouldBeInstanceOf<AcSettingOutOfRange>()
        failure.parameter shouldBe "tempSel"
        failure.value shouldBe "31"
    }

    @Test
    fun `with accepts a range value inside the grid`() {
        // Given: tempSel admits 16..30 step 1
        val command = AcSettingsCommand(catalog, emptyContext)

        // When
        val result = command.with("tempSel", "20")

        // Then
        result.shouldBeRight().parameters()["tempSel"] shouldBe "20"
    }

    @Test
    fun `with rejects a range value off the step grid`() {
        // Given: tempSel admits 16..30 with step 1 — half degrees are off-grid
        val command = AcSettingsCommand(catalog, emptyContext)

        // When
        val result = command.with("tempSel", "20.5")

        // Then
        withClue("20.5 is inside the bounds but not on the 16+n*1 grid") {
            result.shouldBeLeft().shouldBeInstanceOf<AcSettingOutOfRange>()
        }
    }

    @Test
    fun `with rejects a parameter absent from the catalog`() {
        // Given
        val command = AcSettingsCommand(catalog, emptyContext)

        // When
        val result = command.with("ghostParam", "1")

        // Then
        withClue("a parameter the firmware does not declare must never reach the wire") {
            result.shouldBeLeft().shouldBeInstanceOf<AcUnknownParameter>()
                .parameter shouldBe "ghostParam"
        }
    }

    @Test
    fun `with accepts any value on a fixed parameter`() {
        // GUARD (green on arrival, declared): `fixedValue` is a DEFAULT, not a constraint —
        // addhOn's HonParameterFixed setter does not validate ("fixed values are not that
        // fixed after all"). onOffStatus is fixed="1" on this firmware and turning the AC
        // OFF requires sending "0" through it: rejecting non-fixedValue writes would make
        // the device impossible to switch off.
        val command = AcSettingsCommand(catalog, emptyContext)

        // When
        val result = command.with("onOffStatus", "0")

        // Then
        result.shouldBeRight().parameters()["onOffStatus"] shouldBe "0"
    }

    // /// catalog drift (defensive reads) //////////////////////////////////////////////////////
    // The catalog is EXTERNAL input and "the cloud changes shapes without notice": a malformed
    // rule is unenforceable, never a reason to throw through the port or brick the control —
    // the cloud stays the ultimate validator (a bad value returns as a rejected command).

    @Test
    fun `with accepts a range change when the catalog bounds are unreadable`() {
        // Given: a drifted catalog — range typology without minimumValue/maximumValue
        val driftedCatalog = mapper.readTree(
            """
            {"settings": {"setParameters": {"parameters": {
              "tempSel": {"category": "command", "typology": "range", "defaultValue": "22"}
            }}}}
            """,
        )
        val command = AcSettingsCommand(driftedCatalog, emptyContext)

        // When
        val result = command.with("tempSel", "24")

        // Then
        withClue("an unenforceable rule must admit the value, not reject or throw") {
            result.shouldBeRight().parameters()["tempSel"] shouldBe "24"
        }
    }

    @Test
    fun `parameters falls back to 0 for a fixed spec without fixedValue`() {
        // Given: a drifted catalog — fixed typology without its fixedValue
        val driftedCatalog = mapper.readTree(
            """
            {"settings": {"setParameters": {"parameters": {
              "lockStatus": {"category": "command", "typology": "fixed"}
            }}}}
            """,
        )

        // When
        val parameters = AcSettingsCommand(driftedCatalog, emptyContext).parameters()

        // Then: addhOn's base-parameter default, not an NPE
        parameters["lockStatus"] shouldBe "0"
    }

    @Test
    fun `an empty enumValues array is catalog drift, not a rule`() {
        // Given: empty enum sets on a validated AND a sanitized parameter
        val driftedCatalog = mapper.readTree(
            """
            {"settings": {"setParameters": {"parameters": {
              "machMode": {"category": "command", "typology": "enum", "defaultValue": "0", "enumValues": []},
              "windDirectionVertical": {"category": "command", "typology": "enum", "defaultValue": "5", "enumValues": []}
            }}}}
            """,
        )
        val context = mapper.readTree(
            """{"shadow": {"parameters": {"windDirectionVertical": {"parNewVal": "0"}}}}""",
        )
        val command = AcSettingsCommand(driftedCatalog, context)

        // When / Then: no rejection of the change, no crash in the sanitization fallbacks
        command.with("machMode", "4").shouldBeRight()
        withClue("with no admitted set there is nothing to sanitize against") {
            command.parameters()["windDirectionVertical"] shouldBe "0"
        }
    }

    // /// ancillaryParameters //////////////////////////////////////////////////////////////////

    @Test
    fun `ancillaryParameters carries the catalog ancillaries except the rules`() {
        // Given: the real setParameters ancillary block — programRules is a RULE (its
        // fixedValue is an object, not a wire value) and addhOn strips it before sending
        val command = AcSettingsCommand(catalog, emptyContext)

        // When
        val ancillaries = command.ancillaryParameters()

        // Then
        withClue("wire ancillaries only: rules excluded, enum without default resolves to 0") {
            ancillaries shouldBe mapOf(
                "remoteActionable" to "1",
                "remoteVisible" to "1",
                "windDirectionVerticalPositionSequence" to "0",
            )
        }
    }

    @Test
    fun `ancillaryParameters resolves from the context state when present`() {
        // Given: the device reports remoteVisible 0 — the RMW must echo the device truth,
        // not blindly re-send the catalog default (addhOn syncs every parameter group)
        val context = mapper.readTree(
            """{"shadow": {"parameters": {"remoteVisible": {"parNewVal": "0"}}}}""",
        )
        val command = AcSettingsCommand(catalog, context)

        // When
        val ancillaries = command.ancillaryParameters()

        // Then
        withClue("context values must override the ancillary catalog defaults") {
            ancillaries["remoteVisible"] shouldBe "0"
        }
    }

    // /// windDirection sanitization ///////////////////////////////////////////////////////////
    // The device reports windDirection* as "0" while off (LIVE-confirmed on the AS35) but "0"
    // is not among windDirectionVertical's enumValues: sending it back verbatim makes the cloud
    // reject the WHOLE command. Port of addhOn's ac_command.sanitize_wind_direction.

    @Test
    fun `parameters resets an off-enum vertical wind direction to the fixed position 2`() {
        // Given: the REAL off-device state — windDirectionVertical "0", not in [2,4,5,6,7,8]
        val context = mapper.readTree(
            """{"shadow": {"parameters": {"windDirectionVertical": {"parNewVal": "0"}}}}""",
        )

        // When
        val parameters = AcSettingsCommand(catalog, context).parameters()

        // Then
        withClue("vertical must prefer the fixed (non-swing) position 2, never 0 and never 8") {
            parameters["windDirectionVertical"] shouldBe "2"
        }
    }

    @Test
    fun `parameters keeps an admitted vertical wind direction untouched`() {
        // GUARD in the approved batch: sanitization must not rewrite valid state.
        val context = mapper.readTree(
            """{"shadow": {"parameters": {"windDirectionVertical": {"parNewVal": "4"}}}}""",
        )

        // When
        val parameters = AcSettingsCommand(catalog, context).parameters()

        // Then
        parameters["windDirectionVertical"] shouldBe "4"
    }

    @Test
    fun `parameters resets an off-enum horizontal wind direction to the first non-zero value`() {
        // Given: "9" is not in windDirectionHorizontal's [0,3,4,5,6,7]
        val context = mapper.readTree(
            """{"shadow": {"parameters": {"windDirectionHorizontal": {"parNewVal": "9"}}}}""",
        )

        // When
        val parameters = AcSettingsCommand(catalog, context).parameters()

        // Then
        withClue("horizontal picks the first admitted non-zero value") {
            parameters["windDirectionHorizontal"] shouldBe "3"
        }
    }

    @Test
    fun `parameters leaves a wind direction untouched when the catalog has no enum info`() {
        // GUARD in the approved batch: without an admitted set there is nothing to sanitize
        // against — never force a value blindly.
        val enumlessCatalog = mapper.readTree(
            """
            {"settings": {"setParameters": {"parameters": {
              "windDirectionVertical": {"category": "command", "typology": "fixed", "fixedValue": "5"}
            }}}}
            """,
        )
        val context = mapper.readTree(
            """{"shadow": {"parameters": {"windDirectionVertical": {"parNewVal": "0"}}}}""",
        )

        // When
        val parameters = AcSettingsCommand(enumlessCatalog, context).parameters()

        // Then
        parameters["windDirectionVertical"] shouldBe "0"
    }

    @Test
    fun `parameters resolves every catalog parameter to its default when the context is empty`() {
        // Given
        val command = AcSettingsCommand(catalog, emptyContext)

        // When
        val parameters = command.parameters()

        // Then
        withClue("fixed parameters resolve to fixedValue, enum and range to defaultValue") {
            parameters shouldBe mapOf(
                "onOffStatus" to "1",
                "machMode" to "0",
                "tempSel" to "22",
                "windSpeed" to "5",
                "windDirectionVertical" to "5",
                "windDirectionHorizontal" to "0",
            )
        }
    }
}
