package org.agrfesta.sh.api.domain.failures

interface Failure

interface MessageFailure: Failure { val message: String }
interface ExceptionFailure: Failure { val exception: Exception}
