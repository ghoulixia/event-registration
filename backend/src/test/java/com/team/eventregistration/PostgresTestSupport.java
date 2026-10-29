package com.team.eventregistration;

import com.team.eventregistration.service.SlotOfferScheduler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class PostgresTestSupport {
    @MockitoBean
    protected SlotOfferScheduler slotOfferScheduler;

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.jpa.open-in-view", () -> false);
        properties.add("JWT_SECRET", () -> "dGVzdC1vbmx5LXNlY3JldC1mb3ItYXV0aC1pbnRlZ3JhdGlvbi10ZXN0cy0zMi1ieXRlcw==");
        properties.add("app.upload.dir", () -> "target/test-uploads");
    }
}
