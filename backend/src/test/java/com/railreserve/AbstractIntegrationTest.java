package com.railreserve;

import com.railreserve.support.StubPaymentGatewayConfig;
import com.railreserve.support.TestDataFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Base class for integration tests. Every subclass shares the same Spring context and the
 * same Testcontainers PostgreSQL instance (Spring caches the context across test classes
 * that share this configuration, so the container starts once for the whole suite).
 *
 * <p>The background hold-expiry sweep is disabled here so tests drive expiry deterministically.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestDataFactory.class, StubPaymentGatewayConfig.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = "railreserve.booking.scheduler-enabled=false")
public abstract class AbstractIntegrationTest {
}
