package org.agrfesta.sh.api.providers.hon

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

internal val HON_OBJECT_MAPPER = jacksonObjectMapper()

/**
 * Fixed client identity expected by the hOn cloud on every request.
 *
 * Ported from the addhOn project (https://github.com/tis24dev/addhOn, MIT License,
 * Copyright (c) tis24dev). APP_VERSION tracks the real hOn app version: refresh it
 * periodically, the cloud may reject clients that are too old.
 */
internal object HonConstants {
    /** Public OAuth client_id of the hOn mobile app (embedded in the app). */
    const val CLIENT_ID =
        "3MVG9QDx8IX8nP5T2Ha8ofvlmjLZl5L_gvfbT9." +
            "HJvpHGKoAS_dcMN8LYpTSYeVFCraUnV.2Ag1Ki7m4znVO6"

    const val USER_AGENT = "Chrome/999.999.999.999"
    const val APP_VERSION = "2.27.9"
    const val OS = "android"
    const val OS_VERSION = 34
    const val DEVICE_MODEL = "myapp"

    /**
     * The client-identity payload sent to the cloud (`POST /auth/v1/login` and inside
     * every command). With [mobile] = true the `os` key becomes `mobileOs`: the form
     * used inside the `POST /commands/v1/send` body.
     */
    fun devicePayload(mobileId: String, mobile: Boolean = false): ObjectNode =
        HON_OBJECT_MAPPER.createObjectNode().apply {
            put("appVersion", APP_VERSION)
            put("mobileId", mobileId)
            put(if (mobile) "mobileOs" else "os", OS)
            put("osVersion", OS_VERSION)
            put("deviceModel", DEVICE_MODEL)
        }
}
