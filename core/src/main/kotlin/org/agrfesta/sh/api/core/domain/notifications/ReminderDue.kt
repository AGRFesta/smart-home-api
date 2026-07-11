package org.agrfesta.sh.api.core.domain.notifications

import java.time.Duration
import java.time.Instant

/**
 * Pure reminder-cadence decision: whether an OPEN alert is due for a `reminder` notification.
 *
 * Due when the elapsed time since [lastNotifiedAt] reaches [cadence] (boundary included), or when the
 * alert has never been notified — `null` compensates a lost `opened` emission at the next scan.
 *
 * @param lastNotifiedAt when the alert was last successfully notified; `null` means never.
 * @param now the current instant, supplied by the caller (via `TimeProvider`).
 * @param cadence the configured minimum interval between notifications for the same alert.
 */
fun reminderDue(lastNotifiedAt: Instant?, now: Instant, cadence: Duration): Boolean =
    lastNotifiedAt == null || Duration.between(lastNotifiedAt, now) >= cadence
