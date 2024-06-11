package org.agrfesta.sh.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CreativeAssistantApplication

fun main(args: Array<String>) {
    runApplication<CreativeAssistantApplication>(*args)
}
