package com.saif.fitness.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)   // VERY IMPORTANT
@Slf4j
public class GatewayTracingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        ServerHttpRequest req = exchange.getRequest();

        log.info("TRACE-1 [BEFORE ROUTE] Incoming: {} {}", 
                req.getMethod(), req.getURI());

        return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    log.info("TRACE-2 [AFTER ROUTE] Completed for: {} {}", 
                            req.getMethod(), req.getURI());
                })
                .doOnError(err -> {
                    log.error("TRACE-3 [ERROR] on: {} {} -> {}",
                            req.getMethod(), req.getURI(), err.getMessage());
                });
    }
}
