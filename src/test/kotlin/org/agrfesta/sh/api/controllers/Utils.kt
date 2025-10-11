package org.agrfesta.sh.api.controllers

import io.restassured.specification.RequestSpecification
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

fun MockHttpServletRequestBuilder.authenticated() =
    header("Authorization", "Bearer e88230d7d195479dabb1a6650343633f")

fun RequestSpecification.authenticated() =
    header("Authorization", "Bearer e88230d7d195479dabb1a6650343633f")