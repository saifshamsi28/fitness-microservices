package com.saif.fitness.aiservice.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OpenRouterAIService {

    private final WebClient webClient;

    @Value("${openrouter.api.url}")
    private String openRouterUrl;

    @Value("${openrouter.api.key}")
    private String openRouterKey;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(2);
    private static final int MAX_RETRIES = 1;

    public OpenRouterAIService(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    public Mono<String> getRecommendationsAsync(String prompt) {

        Map<String, Object> requestBody = Map.of(
                "model", "deepseek/deepseek-chat",
                "messages", List.of(
                        Map.of("role", "system",
                                "content",
                                "You are a fitness AI. " +
                                        "You MUST return ONLY valid JSON with no markdown, no explanations, no ```json blocks."),
                        Map.of("role", "user", "content", prompt)
                )
        );

        long start = System.currentTimeMillis();

        return webClient.post()
                .uri(openRouterUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + openRouterKey)
                .header("HTTP-Referer", "https://fitness-ai-backend")
                .header("X-Title", "Fitness-AI-Service")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT)
                .retryWhen(
                        Retry.backoff(MAX_RETRIES, RETRY_BACKOFF)
                                .doBeforeRetry(sig ->
                                        log.warn("Retrying OpenRouter request (attempt #{})",
                                                sig.totalRetries() + 1))
                )
                .doOnSuccess(res ->
                        log.info("OpenRouter responded in {} ms",
                                System.currentTimeMillis() - start))
                .doOnError(err ->
                        log.error("OpenRouter call failed: {}", err.getMessage())
                );
    }
}

