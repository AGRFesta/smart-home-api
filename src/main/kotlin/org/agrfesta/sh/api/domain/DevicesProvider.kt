package org.agrfesta.sh.api.domain

interface DevicesProvider {
    fun getAllDevices(): Collection<ProviderDevice>
}
