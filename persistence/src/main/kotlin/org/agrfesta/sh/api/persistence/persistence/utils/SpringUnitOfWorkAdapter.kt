package org.agrfesta.sh.api.persistence.utils

import arrow.core.Either
import org.agrfesta.sh.api.core.application.ports.outbounds.UnitOfWork
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class SpringUnitOfWorkAdapter(
    private val transactionTemplate: TransactionTemplate
) : UnitOfWork {
    override fun <E, A> execute(block: () -> Either<E, A>): Either<E, A> {
        return transactionTemplate.execute { status ->
            val result = block()
            if (result is Either.Left) {
                status.setRollbackOnly()
            }
            result
        }!!
    }
}
