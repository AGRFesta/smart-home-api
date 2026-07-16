package org.agrfesta.sh.api.providers.hon

/** The appliance fields the hOn command endpoints need. */
data class HonApplianceRef(
    val macAddress: String,
    /** Type name (e.g. "AC", "WM"): field `applianceTypeName` of the appliance list. */
    val applianceType: String,
    val applianceModelId: String = "",
    val code: String = "",
    val firmwareId: String? = null,
    val fwVersion: String? = null,
    val series: String? = null,
)
