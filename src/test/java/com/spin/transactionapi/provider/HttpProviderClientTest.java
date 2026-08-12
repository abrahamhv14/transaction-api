package com.spin.transactionapi.provider;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.spin.transactionapi.config.ProviderClientConfig;
import com.spin.transactionapi.domain.TransactionType;
import com.spin.transactionapi.provider.dto.ProviderExecuteRequest;
import com.spin.transactionapi.provider.exception.ProviderCommunicationException;
import com.spin.transactionapi.provider.exception.ProviderRejectedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica el comportamiento del cliente HTTP real (incluyendo retry/circuit
 * breaker) contra un mock del proveedor externo levantado con WireMock,
 * cumpliendo el requerimiento de "mocks del proveedor externo" en testing.
 *
 * Se levanta un contexto mínimo (solo provider + config + Resilience4j) para que
 * Spring AOP teja correctamente las anotaciones @Retry/@CircuitBreaker de
 * Resilience4j sobre HttpProviderClient.
 */
@SpringBootTest(classes = HttpProviderClientTest.TestApplication.class)
@ActiveProfiles("test")
class HttpProviderClientTest {

    @Autowired
    private HttpProviderClient client;

    private static WireMockServer wireMockServer;

    @BeforeEach
    void startWireMock() {
        wireMockServer = new WireMockServer(9090);
        wireMockServer.start();
        configureFor("localhost", 9090);
    }

    @AfterEach
    void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("provider.base-url", () -> "http://localhost:9090");
    }

    private ProviderExecuteRequest sampleRequest() {
        return new ProviderExecuteRequest("acc-123456", TransactionType.CREDIT, new BigDecimal("1500.00"), "MXN");
    }

    @Test
    void returnsExecutionResultWhenProviderApproves() {
        stubFor(post(urlEqualTo("/provider/v1/execute"))
                .willReturn(okJson("""
                        {
                          "transactionId": "txn-789",
                          "status": "APPROVED",
                          "balance": 5500.00,
                          "executedAt": "2025-03-15T10:30:00Z"
                        }
                        """)));

        ProviderExecutionResult result = client.execute(sampleRequest());

        assertThat(result.providerTransactionId()).isEqualTo("txn-789");
        assertThat(result.balanceAfter()).isEqualByComparingTo("5500.00");
    }

    @Test
    void throwsProviderRejectedExceptionWhenProviderRejects() {
        stubFor(post(urlEqualTo("/provider/v1/execute"))
                .willReturn(aResponse().withStatus(422).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "status": "REJECTED",
                                  "code": "INSUFFICIENT_FUNDS",
                                  "message": "The account does not have enough balance to complete the transaction"
                                }
                                """)));

        assertThatThrownBy(() -> client.execute(sampleRequest()))
                .isInstanceOf(ProviderRejectedException.class)
                .hasFieldOrPropertyWithValue("providerCode", "INSUFFICIENT_FUNDS");

        // Un rechazo de negocio no debe reintentarse: solo una llamada.
        verify(1, postRequestedFor(urlEqualTo("/provider/v1/execute")));
    }

    @Test
    void retriesOnTransientServerErrorsAndEventuallyFails() {
        stubFor(post(urlEqualTo("/provider/v1/execute"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.execute(sampleRequest()))
                .isInstanceOf(ProviderCommunicationException.class);

        // max-attempts=3 en application-test.yml/application.yml -> 3 llamadas antes de fallar.
        verify(3, postRequestedFor(urlEqualTo("/provider/v1/execute")));
    }

    /**
     * Contexto mínimo anidado para no ser detectado por el component scan de
     * {@link com.spin.transactionapi.TransactionApiApplication} en otros tests.
     */
    @SpringBootApplication(
            scanBasePackageClasses = {HttpProviderClient.class, ProviderClientConfig.class},
            exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class}
    )
    static class TestApplication {
    }
}
