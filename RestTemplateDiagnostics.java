import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.*;

import java.net.*;

@Slf4j
public class RestTemplateDiagnostics {

    private final RestTemplate restTemplate;

    public RestTemplateDiagnostics(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public <T> T callWithDiagnostics(String url, Class<T> responseType) {
        try {
            ResponseEntity<T> response = restTemplate.getForEntity(url, responseType);
            return response.getBody();

        } catch (ResourceAccessException ex) {
            Throwable root = ex.getCause();

            if (root instanceof SocketTimeoutException) {
                log.error("❌ Timeout: No response within configured time for URL={}", url);
                return null;
            }
            if (root instanceof ConnectException) {
                log.error("❌ Connection refused: Target microservice not reachable for URL={}", url);
                return null;
            }
            if (root instanceof UnknownHostException) {
                log.error("❌ DNS Resolution failed for host in URL={}", url);
                return null;
            }

            log.error("❌ Resource access issue: {}", root != null ? root.getMessage() : ex.getMessage());
            return null;

        } catch (HttpStatusCodeException ex) {
            log.error("❌ Server returned error: status={} body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return null;

        } catch (CallNotPermittedException ex) {
            log.error("❌ Circuit Breaker is OPEN for URL={}", url);
            return null;

        } catch (HttpMessageConversionException ex) {
            log.error("❌ Serialization/Deserialization failed for URL={}", url, ex);
            return null;

        } catch (Exception ex) {
            log.error("❌ Unexpected exception calling {}: {}", url, ex.getMessage(), ex);
            return null;
        }
    }
}
