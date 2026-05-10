package org.agrfesta.sh.api.core.domain.failures

@Deprecated("too generic definition choose a more specific")
interface Failure

data class MessageFailure(val message: String): Failure
interface ExceptionFailure: Failure { val exception: Exception}
