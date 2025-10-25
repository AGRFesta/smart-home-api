package org.agrfesta.sh.api.domain.devices

interface OnOffActuator: Device {
    fun on()
    fun off()
}
