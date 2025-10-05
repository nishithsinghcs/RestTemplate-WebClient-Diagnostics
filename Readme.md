Diagnostic utility in Java that can be used to wrap REST calls and clearly log which failure scenario occurred.
This will help you quickly pinpoint if it’s a timeout, DNS failure, connection pool exhaustion, circuit breaker issue, or serialization error.


Covers timeouts, DNS, connection refused, circuit breaker, serialization, 4xx/5xx errors, retries exhausted.

Connection pool exhaustion → manifests as ResourceAccessException (RestTemplate) or IllegalStateException (WebClient). The diagnostic logs will catch it under "resource access issue" / "unexpected error".

SSL/Cert errors → manifest as SSLHandshakeException, also logged under "unexpected".
