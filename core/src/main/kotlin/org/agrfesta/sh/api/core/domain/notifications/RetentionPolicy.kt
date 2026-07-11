package org.agrfesta.sh.api.core.domain.notifications

import java.time.Duration

/**
 * Retention window for persisted notifications (#194): rows older than [window] are pruned, so the
 * table does not grow without bound. Configured via `alerts.notifications.retention` (ISO-8601
 * duration, default 90 days).
 */
data class RetentionPolicy(val window: Duration)
