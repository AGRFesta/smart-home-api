package org.agrfesta.sh.api.core.application.ports.inbounds.alerts

import org.agrfesta.sh.api.core.domain.devices.Device

interface EvaluateAlertsUseCase {

    /**
     * Evaluates the active alert rules against the freshest cached data for the given [devices],
     * driving the alert lifecycle: it opens, keeps or resolves persisted alerts accordingly.
     *
     * Best-effort by design (it runs on the polling success path): a device whose data is absent or
     * unreadable is skipped — absence of data never resolves an alert — and per-device persistence
     * failures are logged without stopping the remaining evaluations.
     *
     * @param devices the devices to evaluate, as fetched by the current polling cycle.
     */
    fun execute(devices: Collection<Device>)
}
