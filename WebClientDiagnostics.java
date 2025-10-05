import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
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

    public String callWithDiagnostics(String url) {
        try {
            return webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))  // Read timeout
                    .onErrorResume(ex -> {
                        if (ex instanceof SocketTimeoutException) {
                            log.error("❌ Timeout: {}", ex.getMessage());
                            return Mono.just("Timeout error");
                        }
                        if (ex instanceof ConnectException) {
                            log.error("❌ Connection refused: {}", ex.getMessage());
                            return Mono.just("Connection refused");
                        }
                        if (ex instanceof UnknownHostException) {
                            log.error("❌ DNS Resolution failed: {}", ex.getMessage());
                            return Mono.just("DNS resolution failure");
                        }
                        if (ex instanceof CallNotPermittedException) {
                            log.error("❌ Circuit breaker open: {}", ex.getMessage());
                            return Mono.just("Circuit breaker open");
                        }
                        log.error("❌ Unexpected error: {}", ex.getMessage(), ex);
                        return Mono.just("Unexpected error");
                    })
                    .block();

        } catch (Exception ex) {
            log.error("❌ Fatal error in WebClient diagnostics: {}", ex.getMessage(), ex);
            return "Fatal error";
        }
    }
}
