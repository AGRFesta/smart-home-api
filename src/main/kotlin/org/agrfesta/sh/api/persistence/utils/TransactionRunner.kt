package org.agrfesta.sh.api.persistence.utils

import arrow.core.Either
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class TransactionRunner(private val transactionTemplate: TransactionTemplate) {

    fun <E, A> executeInTransaction(block: () -> Either<E, A>): Either<E, A> {
        return transactionTemplate.execute { status ->
            val result = block()
            if (result is Either.Left) {
                status.setRollbackOnly() // rollback!
            }
            result // if resul is Right will commit now
        }!!
    }

}