package org.agrfesta.sh.api.core.application.ports.inbounds

/**
 * Inbound Port: emits a `reminder` notification for every OPEN alert whose last notification is older
 * than the configured cadence (see `docs/domain/ALERTS.md`), re-arming its `lastNotifiedAt`.
 *
 * Driven by a scheduler on a fixed scan; the cadence itself is enforced here, not by the scan frequency.
 *
 * Deliberately returns [Unit]: like `EvaluateAlertsUseCase`, every failure is a tolerated degradation
 * on a scheduled path and the caller has no meaningful branch to take today (typed outcome report
 * planned with the rule abstraction, #195).
 */
interface SendAlertRemindersUseCase {
    fun execute()
}
