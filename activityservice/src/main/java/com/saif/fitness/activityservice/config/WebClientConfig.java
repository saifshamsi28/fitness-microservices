package com.saif.fitness.activityservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced //because of this we can call a service by its registry name so it will automatically assign any available instance of that service
    public WebClient.Builder webClientBuilder(){
        return WebClient.builder();
    }

    @Bean
    public WebClient userServiceWebClient(WebClient.Builder webclientBuilder){
        return webclientBuilder
                .baseUrl("http://USER-SERVICE")
                .build();
    }
}
