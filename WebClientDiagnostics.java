package com.nishithsinghcs.diagnostics;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;

@Slf4j
public class WebClientDiagnostics {

    private final WebClient webClient;

    public WebClientDiagnostics(WebClient webClient) {
        this.webClient = webClient;
    }

    public <T> ResponseWrapper<T> callWithDiagnostics(String url, Class<T> responseType, String tag) {
        long start = System.currentTimeMillis();

        try {
            T result = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(responseType)
                    .timeout(Duration.ofSeconds(5)) // Read timeout
                    .onErrorResume(ex -> {
                        long elapsed = System.currentTimeMillis() - start;

                        if (ex instanceof SocketTimeoutException) {
                            log.error("❌ [{}] Timeout: URL={} time={}ms", tag, url, elapsed);
                            return Mono.empty();
                        }
                        if (ex instanceof ConnectException) {
                            log.error("❌ [{}] Connection refused: URL={} time={}ms", tag, url, elapsed);
                            return Mono.empty();
                        }
                        if (ex instanceof UnknownHostException) {
                            log.error("❌ [{}] DNS Resolution failed: URL={} time={}ms", tag, url, elapsed);
                            return Mono.empty();
                        }
                        if (ex instanceof CallNotPermittedException) {
                            log.error("❌ [{}] Circuit breaker open: URL={} time={}ms", tag, url, elapsed);
                            return Mono.empty();
                        }
                        if (ex instanceof WebClientResponseException wcre) {
                            log.error("❌ [{}] HTTP error: URL={} status={} body={} time={}ms",
                                    tag, url, wcre.getStatusCode(), wcre.getResponseBodyAsString(), elapsed);
                            return Mono.empty();
                        }

                        log.error("❌ [{}] Unexpected error: URL={} error={} time={}ms",
                                tag, url, ex.getMessage(), elapsed, ex);
                        return Mono.empty();
                    })
                    .block();

            long elapsed = System.currentTimeMillis() - start;

            if (result != null) {
                log.info("✅ [{}] Success: URL={} time={}ms", tag, url, elapsed);
                return new ResponseWrapper<>(HttpStatus.OK, result, null, elapsed, tag);
            } else {
                return new ResponseWrapper<>(HttpStatus.INTERNAL_SERVER_ERROR, null,
                        "No response body", elapsed, tag);
            }

        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("❌ [{}] Fatal error in WebClient diagnostics: URL={} error={} time={}ms",
                    tag, url, ex.getMessage(), elapsed, ex);
            return new ResponseWrapper<>(HttpStatus.INTERNAL_SERVER_ERROR, null,
                    "Fatal error: " + ex.getMessage(), elapsed, tag);
        }
    }
}
