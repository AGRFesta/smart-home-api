package org.agrfesta.sh.api

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestContainersConfig::class)
@Testcontainers
@ActiveProfiles("netatmo-only-test")
class SmartHomeApplicationNetatmoOnlyTest {
    @Test fun `context loads with only Netatmo configured`() { /* just loads context */ }
}
