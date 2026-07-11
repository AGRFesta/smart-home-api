package org.agrfesta.sh.api.core.domain.failures

/**
 * Groups all causes of a failure while reading alerts.
 */
sealed interface GetAlertsFailure

/**
 * Groups all causes of a failure while creating an alert.
 */
sealed interface AlertCreationFailure

/**
 * Groups all causes of a failure while persisting the resolution of an alert.
 */
sealed interface AlertResolutionFailure

/**
 * Groups all causes of a failure while tracking the instant an alert was last notified.
 */
sealed interface AlertNotificationTrackingFailure

/** Infrastructure-level failure while accessing alert persistence. */
data object AlertRepositoryError :
    GetAlertsFailure, AlertCreationFailure, AlertResolutionFailure, AlertNotificationTrackingFailure

/** An OPEN alert already exists for the same `(type, target)`: idempotency guard at the storage layer. */
data object AlertAlreadyOpen : AlertCreationFailure
