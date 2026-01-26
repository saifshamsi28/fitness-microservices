package com.saif.fitness.gateway;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class LoggingFilter {

    @Bean
    public GlobalFilter logRequestResponseFilter() {
        return (exchange, chain) -> {
            var request = exchange.getRequest();

            System.out.println("=================================");
            System.out.println("Incoming request URI : " + request.getURI());
            System.out.println("Incoming request path: " + request.getPath());

            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                var response = exchange.getResponse();
                System.out.println("Response status code : " + response.getStatusCode());
    System.out.println("Target URI (routed)  : " +
            exchange.getAttribute(
       "org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayRequestUrl"));
System.out.println("=================================");
            }));
        };
    }
}