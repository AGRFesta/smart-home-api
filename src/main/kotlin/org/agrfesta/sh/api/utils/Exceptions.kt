package org.agrfesta.sh.api.utils

fun Exception.toDetailedString(): String {
    val exceptionName = this::class.simpleName ?: "UnknownException"
    val messageText = this.message?.takeIf { it.isNotBlank() } ?: "Nessun messaggio disponibile"
    val causeMessage = this.cause?.let { " | Causa: ${it.message ?: "Nessun messaggio disponibile"}" } ?: ""

    return "Errore: $exceptionName - $messageText$causeMessage"
}
