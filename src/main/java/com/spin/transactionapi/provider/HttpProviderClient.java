package com.spin.transactionapi.provider;

import com.spin.transactionapi.provider.dto.ProviderErrorResponse;
import com.spin.transactionapi.provider.dto.ProviderExecuteRequest;
import com.spin.transactionapi.provider.dto.ProviderExecuteResponse;
import com.spin.transactionapi.provider.exception.ProviderCommunicationException;
import com.spin.transactionapi.provider.exception.ProviderRejectedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Implementación HTTP de {@link ProviderClient}.
 *
 * Resiliencia (Resilience4j):
 *  - Retry: reintenta automáticamente errores transitorios (timeouts, 5xx).
 *  - CircuitBreaker: si el proveedor está caído de forma sostenida, deja de
 *    golpearlo y falla rápido, protegiendo el throughput del sistema.
 *  - Timeouts de conexión/lectura se configuran directamente en el
 *    RestClient (ver ProviderClientConfig), ya que el cliente es síncrono.
 *
 * Los rechazos de negocio (4xx con código como INSUFFICIENT_FUNDS) NO se
 * reintentan ni cuentan como fallos del circuit breaker: son un resultado
 * de negocio válido, no una falla técnica.
 */
@Slf4j
@Component
public class HttpProviderClient implements ProviderClient {

    private static final String EXECUTE_PATH = "/provider/v1/execute";

    private final RestClient restClient;

    public HttpProviderClient(RestClient providerRestClient) {
        this.restClient = providerRestClient;
    }

    @Override
    @CircuitBreaker(name = "providerClient", fallbackMethod = "fallback")
    @Retry(name = "providerClient")
    public ProviderExecutionResult execute(ProviderExecuteRequest request) {
        try {
            ProviderExecuteResponse response = restClient.post()
                    .uri(EXECUTE_PATH)
                    .body(request)
                    .retrieve()
                    .body(ProviderExecuteResponse.class);

            if (response == null) {
                throw new ProviderCommunicationException("Respuesta vacía del proveedor externo", null);
            }

            return new ProviderExecutionResult(
                    response.transactionId(),
                    response.balance(),
                    response.executedAt()
            );

        } catch (HttpStatusCodeException ex) {
            // El proveedor respondió con 4XX/5XX -> intentamos leer el body de error de negocio.
            ProviderErrorResponse error = parseProviderError(ex);
            if (error != null && error.code() != null) {
                // Rechazo de negocio explícito (ej. INSUFFICIENT_FUNDS): no es un fallo técnico,
                // se propaga tal cual y NO debe reintentarse (ver RetryConfig / ignoreExceptions).
                throw new ProviderRejectedException(error.code(), error.message());
            }
            throw ex; // 5xx sin body reconocible -> se trata como fallo técnico (retry/circuit breaker)
        } catch (ResourceAccessException ex) {
            // Timeout o error de red -> fallo técnico, elegible para retry.
            throw ex;
        }
    }

    private ProviderErrorResponse parseProviderError(HttpStatusCodeException ex) {
        try {
            return ex.getResponseBodyAs(ProviderErrorResponse.class);
        } catch (Exception parsingError) {
            log.warn("No se pudo parsear el body de error del proveedor: {}", parsingError.getMessage());
            return null;
        }
    }

    /**
     * Fallback invocado cuando el circuit breaker está abierto o se agotan
     * los reintentos por fallos técnicos. Los rechazos de negocio
     * (ProviderRejectedException) se re-lanzan tal cual, sin pasar por aquí
     * como fallo técnico.
     */
    private ProviderExecutionResult fallback(ProviderExecuteRequest request, ProviderRejectedException ex) {
        throw ex;
    }

    private ProviderExecutionResult fallback(ProviderExecuteRequest request, Throwable ex) {
        log.error("Fallo de comunicación con el proveedor externo para accountId={}: {}",
                request.accountId(), ex.getMessage());
        throw new ProviderCommunicationException(
                "No fue posible comunicarse con el proveedor externo, intenta más tarde", ex);
    }
}
