package com.nishithsingh.diagnostics;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.*;

import java.net.*;

@Slf4j
public class RestTemplateDiagnostics {

    private final RestTemplate restTemplate;

    public RestTemplateDiagnostics(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public <T> ResponseWrapper<T> callWithDiagnostics(String url, Class<T> responseType) {
        try {
            ResponseEntity<T> response = restTemplate.getForEntity(url, responseType);
            return new ResponseWrapper<>(response.getStatusCode(), response.getBody(), null);

        } catch (ResourceAccessException ex) {
            Throwable root = ex.getCause();

            if (root instanceof SocketTimeoutException) {
                log.error("❌ Timeout for URL={}", url);
                return new ResponseWrapper<>(HttpStatus.GATEWAY_TIMEOUT, null, "Timeout error");
            }
            if (root instanceof ConnectException) {
                log.error("❌ Connection refused for URL={}", url);
                return new ResponseWrapper<>(HttpStatus.SERVICE_UNAVAILABLE, null, "Connection refused");
            }
            if (root instanceof UnknownHostException) {
                log.error("❌ DNS resolution failed for URL={}", url);
                return new ResponseWrapper<>(HttpStatus.SERVICE_UNAVAILABLE, null, "DNS resolution failed");
            }

            log.error("❌ Resource access issue: {}", root != null ? root.getMessage() : ex.getMessage());
            return new ResponseWrapper<>(HttpStatus.INTERNAL_SERVER_ERROR, null, "Resource access error");

        } catch (HttpStatusCodeException ex) {
            log.error("❌ Server returned error: status={} body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return new ResponseWrapper<>(ex.getStatusCode(), null, "Server error: " + ex.getStatusText());

        } catch (CallNotPermittedException ex) {
            log.error("❌ Circuit Breaker OPEN for URL={}", url);
            return new ResponseWrapper<>(HttpStatus.SERVICE_UNAVAILABLE, null, "Circuit breaker open");

        } catch (HttpMessageConversionException ex) {
            log.error("❌ Serialization/Deserialization failed for URL={}", url, ex);
            return new ResponseWrapper<>(HttpStatus.INTERNAL_SERVER_ERROR, null, "Serialization error");

        } catch (Exception ex) {
            log.error("❌ Unexpected exception calling {}: {}", url, ex.getMessage(), ex);
            return new ResponseWrapper<>(HttpStatus.INTERNAL_SERVER_ERROR, null, "Unexpected error: " + ex.getMessage());
        }
    }
}
