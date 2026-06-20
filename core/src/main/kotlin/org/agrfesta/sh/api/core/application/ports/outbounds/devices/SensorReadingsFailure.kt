package org.agrfesta.sh.api.core.application.ports.outbounds.devices

interface SensorReadingsFailure

data class FailureByException(val reason: Exception) : SensorReadingsFailure
