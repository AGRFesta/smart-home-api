package org.agrfesta.sh.api.persistence

import org.agrfesta.sh.api.domain.AreaWithDevices

interface AreasWithDevicesDao {

    /**
     * Fetches all areas and related devices.
     */
    fun getAllAreasWithDevices(): Collection<AreaWithDevices>

}
