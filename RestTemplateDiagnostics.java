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

    public <T> ResponseWrapper<T> callWithDiagnostics(String url, Class<T> responseType, String tag) {
        long start = System.currentTimeMillis();

        try {
            ResponseEntity<T> response = restTemplate.getForEntity(url, responseType);
            long elapsed = System.currentTimeMillis() - start;

            log.info("✅ [{}] Success: URL={} status={} time={}ms",
                     tag, url, response.getStatusCode(), elapsed);

            return new ResponseWrapper<>(response.getStatusCode(),
                                         response.getBody(),
                                         null,
                                         elapsed,
                                         tag);

        } catch (ResourceAccessException ex) {
            long elapsed = System.currentTimeMillis() - start;
            Throwable root = ex.getCause();

            if (root instanceof SocketTimeoutException) {
                log.error("❌ [{}] Timeout: URL={} time={}ms", tag, url, elapsed);
                return new ResponseWrapper<>(HttpStatus.GATEWAY_TIMEOUT, null, "Timeout error", elapsed, tag);
            }
            if (root instanceof ConnectException) {
                log.error("❌ [{}] Connection refused: URL={} time={}ms", tag, url, elapsed);
                return new ResponseWrapper<>(HttpStatus.SERVICE_UNAVAILABLE, null, "Connection refused", elapsed, tag);
            }
            if (root instanceof UnknownHostException) {
                log.error("❌ [{}] DNS failure: URL={} time={}ms", tag, url, elapsed);
                return new ResponseWrapper<>(HttpStatus.SERVICE_UNAVAILABLE, null, "DNS resolution failed", elapsed, tag);
            }

            log.error("❌ [{}] Resource access issue: {} URL={} time={}ms",
                      tag, root != null ? root.getMessage() : ex.getMessage(), url, elapsed);
            return new ResponseWrapper<>(HttpStatus.INTERNAL_SERVER_ERROR, null, "Resource access error", elapsed, tag);

        } catch (HttpStatusCodeException ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("❌ [{}] Server error: URL={} status={} body={} time={}ms",
                      tag, url, ex.getStatusCode(), ex.getResponseBodyAsString(), elapsed);
            return new ResponseWrapper<>(ex.getStatusCode(), null,
                                         "Server error: " + ex.getStatusText(), elapsed, tag);

        } catch (CallNotPermittedException ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("❌ [{}] Circuit breaker open: URL={} time={}ms", tag, url, elapsed);
            return new ResponseWrapper<>(HttpStatus.SERVICE_UNAVAILABLE, null,
                                         "Circuit breaker open", elapsed, tag);

        } catch (HttpMessageConversionException ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("❌ [{}] Serialization/Deserialization failed: URL={} time={}ms",
                      tag, url, elapsed, ex);
            return new ResponseWrapper<>(HttpStatus.INTERNAL_SERVER_ERROR, null,
                                         "Serialization error", elapsed, tag);

        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("❌ [{}] Unexpected exception: URL={} error={} time={}ms",
                      tag, url, ex.getMessage(), elapsed);
            return new ResponseWrapper<>(HttpStatus.INTERNAL_SERVER_ERROR, null,
                                         "Unexpected error: " + ex.getMessage(), elapsed, tag);
        }
    }
}
