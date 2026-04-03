package org.agrfesta.sh.api

import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension

class CleanSmartHomeDatabaseExtension : BeforeEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        SpringExtension.getApplicationContext(context)
            .getBean(JdbcTemplate::class.java)
            .execute("""
                DO ${'$'}${'$'} DECLARE
                    r RECORD;
                BEGIN
                    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'smart_home' AND tablename != 'flyway_schema_history') LOOP
                        EXECUTE 'TRUNCATE TABLE smart_home.' || quote_ident(r.tablename) || ' CASCADE';
                    END LOOP;
                END ${'$'}${'$'};
            """)
    }
}
