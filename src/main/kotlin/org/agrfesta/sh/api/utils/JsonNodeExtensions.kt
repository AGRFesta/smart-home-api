package org.agrfesta.sh.api.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeFormatter.ISO_DATE_TIME

fun JsonNode.isMissingOrNull(): Boolean = isNull || isMissingNode

fun JsonNode.findNodeAt(jsonPtrExpr: String): JsonNode? {
    val node = at(jsonPtrExpr)
    return if (node.isMissingOrNull()) null else node
}
//fun JsonNode.getNodeAt(jsonPtrExpr: String): JsonNode {
//    val node = at(jsonPtrExpr)
//    if (node.isMissingOrNull()) contractBreak("Node missing at $jsonPtrExpr!")
//    return node
//}

fun JsonNode.findTextAt(jsonPtrExpr: String): String? {
    val node = at(jsonPtrExpr)
    if (node.isMissingOrNull() || !node.isValueNode) return null
    return node.asText()
}
//fun JsonNode.getTextAt(jsonPtrExpr: String): String {
//    val node = at(jsonPtrExpr)
//    if (node.isMissingOrNull()) contractBreak("String missing at $jsonPtrExpr!")
//    if (!node.isValueNode) contractBreak("Node at $jsonPtrExpr is not a value!")
//    return node.asText()
//}

fun JsonNode.findIntAt(jsonPtrExpr: String): Int? {
    val node = at(jsonPtrExpr)
    if (node.isMissingOrNull() || !node.isValueNode) return null
    return node.asInt()
}
//fun JsonNode.getIntAt(jsonPtrExpr: String): Int {
//    val node = at(jsonPtrExpr)
//    if (node.isMissingOrNull()) contractBreak("Int missing at $jsonPtrExpr!")
//    return node.asInt()
//}

//fun JsonNode.getLongAt(jsonPtrExpr: String): Long {
//    val node = at(jsonPtrExpr)
//    if (node.isMissingOrNull()) contractBreak("Long missing at $jsonPtrExpr!")
//    return node.asLong()
//}
//
//fun JsonNode.getDoubleAt(jsonPtrExpr: String): Double {
//    val node = at(jsonPtrExpr)
//    if (node.isMissingOrNull()) contractBreak("Double missing at $jsonPtrExpr!")
//    return node.asDouble()
//}

fun JsonNode.findFloatAt(jsonPtrExpr: String): Float? {
    val node = at(jsonPtrExpr)
    if (node.isMissingOrNull() || !node.isValueNode) return null
    return node.asDouble().toFloat()
}
//fun JsonNode.getFloatAt(jsonPtrExpr: String): Float {
//    val node = at(jsonPtrExpr)
//    if (node.isMissingOrNull()) contractBreak("Float missing at $jsonPtrExpr!")
//    return node.asDouble().toFloat()
//}

fun JsonNode.findBigDecimalAt(jsonPtrExpr: String): BigDecimal? {
    val node = at(jsonPtrExpr)
    if (node.isMissingOrNull() || !node.isValueNode) return null
    return node.asDouble().toBigDecimal()
}
//fun JsonNode.getBigDecimalAt(jsonPtrExpr: String): BigDecimal {
//    val node = at(jsonPtrExpr)
//    if (node.isMissingOrNull()) contractBreak("BigDecimal missing at $jsonPtrExpr!")
//    return node.asDouble().toBigDecimal()
//}

fun JsonNode.findInstantAt(jsonPtrExpr: String): Instant? {
    val node = at(jsonPtrExpr)
    if (node.isMissingOrNull() || !node.isValueNode) return null
    return Instant.from(ISO_DATE_TIME.parse(node.asText()))
}
//fun JsonNode.getInstantAt(jsonPtrExpr: String): Instant {
//    val node = at(jsonPtrExpr)
//    if (node.isMissingOrNull()) contractBreak("Instant missing at $jsonPtrExpr!")
//    if (!node.isValueNode) contractBreak("Node at $jsonPtrExpr is not a value!")
//    return Instant.from(ISO_DATE_TIME.parse(node.asText()))
//}
//
//fun <T> JsonNode.getListAt(jsonPtrExpr: String, mapper: (JsonNode) -> T): List<T> {
//    val node = at(jsonPtrExpr)
//    if (node.isMissingOrNull()) contractBreak("Array missing at $jsonPtrExpr!")
//    if (node !is ArrayNode) contractBreak("Property at $jsonPtrExpr is not an array!")
//    return node.map { mapper.invoke(it) }
//}

fun JsonNode.findArrayNodeAt(jsonPtrExpr: String): ArrayNode? {
    val node = at(jsonPtrExpr)
    if (node.isMissingOrNull() || node !is ArrayNode) return null
    return node
}
//fun JsonNode.getArrayNodeAt(jsonPtrExpr: String): ArrayNode {
//    val node = at(jsonPtrExpr)
//    if (node.isMissingOrNull()) contractBreak("Array missing at $jsonPtrExpr!")
//    if (node !is ArrayNode) contractBreak("Property at $jsonPtrExpr is not an array!")
//    return node
//}

//fun JsonNode.getBooleanAt(jsonPtrExpr: String): Boolean {
//    val node = at(jsonPtrExpr)
//    if (node.isMissingOrNull()) contractBreak("Boolean missing at $jsonPtrExpr!")
//    return node.asBoolean()
//}