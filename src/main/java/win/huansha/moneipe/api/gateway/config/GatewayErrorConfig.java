package win.huansha.moneipe.api.gateway.config;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Handler propio de errores: 503 destino caído · 504 timeout · 500 interno.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GatewayErrorConfig {

    private final ObjectMapper objectMapper;

    @Bean
    @Order(-2) // antes que el handler por defecto de Spring Boot
    ErrorWebExceptionHandler gatewayExceptionHandler() {
        return (exchange, ex) -> {
            if (exchange.getResponse().isCommitted()) {
                return Mono.error(ex);
            }

            HttpStatus status = resolveStatus(ex);
            logDownstream(ex, status, exchange);

            exchange.getResponse().setStatusCode(status);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

            byte[] body = toJson(buildBody(exchange, ex, status));
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        };
    }

    private HttpStatus resolveStatus(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
            if (cause instanceof SocketTimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            if (cause.getCause() == null) {
                break;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private void logDownstream(Throwable ex, HttpStatus status, ServerWebExchange exchange) {
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.error("Error no manejado en {} {}",
                    exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(), ex);
        } else {
            log.warn("{} {} → {} ({}: {})",
                    exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(),
                    status.value(), ex.getClass().getSimpleName(), rootMessage(ex));
        }
    }

    private Map<String, Object> buildBody(ServerWebExchange exchange, Throwable ex, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("path", exchange.getRequest().getPath().value());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", status == HttpStatus.SERVICE_UNAVAILABLE
                ? "Microservicio no disponible"
                : rootMessage(ex));
        body.put("requestId", exchange.getRequest().getId());
        return body;
    }

    private String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
    }

    private byte[] toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            return ("{\"status\":500,\"error\":\"Internal Server Error\"}").getBytes();
        }
    }
}
