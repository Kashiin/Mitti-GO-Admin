package uz.mittisoftware.mittigo.admin.backend;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calls the server's admin API (`/admin/**`) with the signed-in admin's Bearer token. Errors become [BackendException];
 * the token is never logged.
 */
@Component
public class BackendClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    // Adding a recommendation asks YouTube, so allow it some time.
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    // A channel-list import checks every row on YouTube one by one: a few hundred rows take a few minutes.
    private static final Duration LONG_READ_TIMEOUT = Duration.ofMinutes(5);

    private final RestClient rest;
    private final RestClient longRest;
    private final JsonMapper json;

    BackendClient(@Value("${mittigo.backend-url}") String baseUrl, JsonMapper json) {
        this.json = json;
        this.rest = client(baseUrl, READ_TIMEOUT);
        this.longRest = client(baseUrl, LONG_READ_TIMEOUT);
    }

    /** POST that may run for minutes (imports): waits up to [LONG_READ_TIMEOUT] instead of [READ_TIMEOUT]. */
    public <T> T postLong(String token, String path, Object body, Class<T> type, Object... uriVariables) {
        return call(() -> longRest.post().uri(path, uriVariables).headers(auth(token)).body(body).retrieve().body(type));
    }

    private RestClient client(String baseUrl, Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultStatusHandler(status -> status.isError(), (request, response) -> {
                    throw toException(response);
                })
                .build();
    }

    public <T> T get(String token, String path, Class<T> type, Object... uriVariables) {
        return call(() -> rest.get().uri(path, uriVariables).headers(auth(token)).retrieve().body(type));
    }

    public <T> T get(String token, String path, ParameterizedTypeReference<T> type, Object... uriVariables) {
        return call(() -> rest.get().uri(path, uriVariables).headers(auth(token)).retrieve().body(type));
    }

    public <T> T post(String token, String path, Object body, Class<T> type, Object... uriVariables) {
        return call(() -> rest.post().uri(path, uriVariables).headers(auth(token)).body(body == null ? Map.of() : body).retrieve().body(type));
    }

    public <T> T put(String token, String path, Object body, Class<T> type, Object... uriVariables) {
        return call(() -> rest.put().uri(path, uriVariables).headers(auth(token)).body(body).retrieve().body(type));
    }

    public void delete(String token, String path, Object... uriVariables) {
        call(() -> rest.delete().uri(path, uriVariables).headers(auth(token)).retrieve().toBodilessEntity());
    }

    private static Consumer<HttpHeaders> auth(String token) {
        return headers -> {
            if (token != null) headers.setBearerAuth(token);
        };
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (ResourceAccessException e) {
            throw new BackendException(0, "NO_CONNECTION", "No connection", null, null);
        }
    }

    @SuppressWarnings("unchecked")
    private BackendException toException(ClientHttpResponse response) {
        int status = 0;
        String retryAfter = null;

        try {
            status = response.getStatusCode().value();
            retryAfter = response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
            Map<String, Object> body = json.readValue(response.getBody(), Map.class);
            Map<String, Object> error = (Map<String, Object>) body.get("error");

            return new BackendException(
                    status,
                    (String) error.get("code"),
                    (String) error.get("message"),
                    (List<Map<String, String>>) error.get("fields"),
                    retryAfter == null ? null : Long.valueOf(retryAfter));
        } catch (Exception e) {
            return new BackendException(status, "SERVER_ERROR", "Unreadable error", null, null);
        }
    }
}
