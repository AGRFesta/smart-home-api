package org.agrfesta.test.mothers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.*

fun aRandomUniqueString(): String = UUID.randomUUID().toString()

fun anUrl(): String = "https://${aRandomUniqueString()}.org"

fun aJsonNode(): JsonNode {
    val mapper = ObjectMapper()
    val rootNode: ObjectNode = mapper.createObjectNode()
    rootNode.put("property", aRandomUniqueString())
    return rootNode
}
