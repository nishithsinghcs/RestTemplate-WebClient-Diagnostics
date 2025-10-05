package com.nishithsinghcs.diagnostics;

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
        long start = System.currentTimeMillis();

        try {
            ResponseEntity<T> response = restTemplate.getForEntity(url, responseType);
            long elapsed = System.currentTimeMillis() - start;

            log.info("✅ Success: URL={} status={} time={}ms", url, response.getStatusCode(), elapsed);
            return new ResponseWrapper<>(response.getStatusCode(), response.getBody(), null, elapsed);

        } catch (ResourceAccessException ex) {
            long elapsed = System.currentTimeMillis() - start;
            Throwable root = ex.getCause();

            if (root instanceof SocketTimeoutException) {
                log.error("❌ Timeout: URL={} time={}ms", url, elapsed);
                return new ResponseWrapper<>(HttpStatus.GATEWAY_TIMEOUT, null, "Timeout error", elapsed);
            }
            if (root instanceof ConnectException) {
                log.error("❌ Connection refused: URL={} time={}ms", url, elapsed);
                return new ResponseWrapper<>(HttpStatus.SERVICE_UNAVAILABLE, null, "Connection refused", elapsed);
            }
            if (root instanceof UnknownHostException) {
                log.error("❌ DNS failure: URL={} time={}ms", url, elapsed);
                return new ResponseWrapper<>(HttpStatus.SERVICE_UNAVAILABLE, null, "DNS resolution failed", elapsed);
            }

            log.error("❌ Resource access issue: {} URL={} time={}ms", 
                      root != null ? root.getMessage() : ex.getMessage(), url, elapsed);
            return new ResponseWrapper<>(HttpStatus.INTERNAL_SERVER_ERROR, null, "Resource access error", elapsed);

        } catch (HttpStatusCodeException ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("❌ Server error: URL={} status={} body={} time={}ms", 
                      url, ex.getStatusCode(), ex.getResponseBodyAsString(), elapsed);
            return new ResponseWrapper<>(ex.getStatusCode(), null, "Server error: " + ex.getStatusText(), elapsed);

        } catch (CallNotPermittedException ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("❌ Circuit breaker open: URL={} time={}ms", url, elapsed);
            return new ResponseWrapper<>(HttpStatus.SERVICE_UNAVAILABLE, null, "Circuit breaker open", elapsed);

        } catch (HttpMessageConversionException ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("❌ Serialization/Deserialization failed: URL={} time={}ms", url, elapsed, ex);
            return new ResponseWrapper<>(HttpStatus.INTERNAL_SERVER_ERROR, null, "Serialization error", elapsed);

        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("❌ Unexpected exception: URL={} error={} time={}ms", url, ex.getMessage(), elapsed);
            return new ResponseWrapper<>(HttpStatus.INTERNAL_SERVER_ERROR, null, "Unexpected error: " + ex.getMessage(), elapsed);
        }
    }
}
