package org.agrfesta.sh.api.controllers

import io.restassured.specification.RequestSpecification
import org.agrfesta.sh.api.core.domain.devices.Device
import org.agrfesta.test.mothers.aRandomUniqueString
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

fun DeviceResponse.toDevice() = Device(
    uuid = uuid,
    status = status,
    deviceProviderId = deviceProviderId,
    provider = provider,
    name = name,
    features = features
)

fun MockHttpServletRequestBuilder.authenticated() =
    header("Authorization", "Bearer e88230d7d195479dabb1a6650343633f")

fun RequestSpecification.authenticated(): RequestSpecification =
    header("Authorization", "Bearer e88230d7d195479dabb1a6650343633f")

fun RequestSpecification.wrongAuthentication(): RequestSpecification =
    header("Authorization", "Bearer ${aRandomUniqueString()}")
