package org.agrfesta.sh.api

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.restassured.RestAssured
import org.agrfesta.sh.api.providers.switchbot.SwitchBotDevicesClient
import org.agrfesta.sh.api.utils.RandomGenerator
import org.agrfesta.sh.api.utils.TimeService
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestConstructor
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SmartHomeTestConfiguration::class, TestContainersConfig::class)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {
    @SpykBean
    protected lateinit var randomGenerator: RandomGenerator
    @SpykBean
    protected lateinit var timeService: TimeService
    @MockkBean
    protected lateinit var switchBotDevicesClient: SwitchBotDevicesClient

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate
    @Autowired
    protected lateinit var redisTemplate: RedisTemplate<String, Any>

    @LocalServerPort
    private val port: Int? = null

    @BeforeEach
    fun setUp() {
        RestAssured.baseURI = "http://localhost:$port"

        // Postgres cleanup (ignores the Flyway/Liquibase history table)
        jdbcTemplate.execute("""
            DO $$ DECLARE
                r RECORD;
            BEGIN
                FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = current_schema() AND tablename != 'flyway_schema_history') LOOP
                    EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' CASCADE';
                END LOOP;
            END $$;
        """)

        // Redis cleanup
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
    }

}