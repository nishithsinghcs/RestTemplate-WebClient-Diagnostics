import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
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

    // Generic version
    public <T> T callWithDiagnostics(String url, Class<T> responseType) {
        try {
            return webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(responseType)
                    .timeout(Duration.ofSeconds(5)) // Read timeout
                    .onErrorResume(ex -> {
                        if (ex instanceof SocketTimeoutException) {
                            log.error("❌ Timeout: {}", ex.getMessage());
                            return Mono.empty();
                        }
                        if (ex instanceof ConnectException) {
                            log.error("❌ Connection refused: {}", ex.getMessage());
                            return Mono.empty();
                        }
                        if (ex instanceof UnknownHostException) {
                            log.error("❌ DNS Resolution failed: {}", ex.getMessage());
                            return Mono.empty();
                        }
                        if (ex instanceof CallNotPermittedException) {
                            log.error("❌ Circuit breaker open: {}", ex.getMessage());
                            return Mono.empty();
                        }
                        if (ex instanceof WebClientResponseException wcre) {
                            log.error("❌ HTTP error: status={} body={}", wcre.getStatusCode(), wcre.getResponseBodyAsString());
                            return Mono.empty();
                        }
                        log.error("❌ Unexpected error: {}", ex.getMessage(), ex);
                        return Mono.empty();
                    })
                    .block();

        } catch (Exception ex) {
            log.error("❌ Fatal error in WebClient diagnostics: {}", ex.getMessage(), ex);
            return null;
        }
    }
}
