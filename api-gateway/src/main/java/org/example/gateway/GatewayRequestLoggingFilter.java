
package org.example.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayRequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(GatewayRequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        long started = System.nanoTime();

        return chain.filter(exchange).doFinally(signal -> {
            long elapsed = (System.nanoTime() - started) / 1_000_000;

            var response = exchange.getResponse();
            var status = response.getStatusCode();

            log.info(
                    "GATEWAY {} {} status={} durationMs={}",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value(),
                    status == null ? "UNKNOWN" : status.value(),
                    elapsed
            );
        });
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
