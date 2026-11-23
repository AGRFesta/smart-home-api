package org.agrfesta.sh.api.providers.netatmo

import java.io.IOException

/** A request to Netatmo that ended without a response: a timeout or a connection that failed. */
data class NetatmoConnectionFailure(val cause: IOException) : NetatmoClientFailure
