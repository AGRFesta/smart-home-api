package org.agrfesta.sh.api.core.domain.notifications

import java.time.Duration

/**
 * Global reminder cadence policy (#194): the minimum interval between two notifications for the same
 * OPEN alert. Configured via `alerts.notifications.reminder-interval` (ISO-8601 duration, default daily);
 * per-device cadence is deferred to a later issue.
 */
data class ReminderPolicy(val cadence: Duration)
