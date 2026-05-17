package org.agrfesta.sh.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SmartHomeApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<SmartHomeApplication>(*args)
}
