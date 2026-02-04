//package com.saif.fitness.aiservice.service;
//
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.client.WebClient;
//import reactor.core.publisher.Mono;
//import reactor.util.retry.Retry;
//
//import java.time.Duration;
//import java.util.Map;
//
//@Service
//@Slf4j
//public class GeminiService {
//
//    private final WebClient webClient;
//
//    @Value("${gemini.api.url}")
//    private String geminiApiUrl;
//
//    @Value("${gemini.api.key}")
//    private String geminiApiKey;
//
//    // ===== CONFIG YOU CAN TUNE EASILY =====
//    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
//    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(3);
//    private static final int MAX_RETRIES = 1;   // only 1 retry
//    // =====================================
//
//    public GeminiService(WebClient.Builder webClientBuilder) {
//        this.webClient = webClientBuilder.build();
//    }
//
//    public Mono<String> getRecommendationsAsync(String details) {
//
//        Map<String, Object> requestBody = Map.of(
//                "contents", new Object[]{
//                        Map.of("parts", new Object[]{
//                                Map.of("text", details)
//                        })
//                }
//        );
//
//        long start = System.currentTimeMillis();
//
//        log.info("Sending async request to Gemini...");
//
//        return webClient.post()
//                .uri(geminiApiUrl)
//                .contentType(MediaType.APPLICATION_JSON)
//                .header("X-goog-api-key", geminiApiKey)
//                .bodyValue(requestBody)
//                .retrieve()
//                .onStatus(
//                        status -> status.value() == 429,
//                        response -> Mono.error(new GeminiRateLimitException("Gemini 429"))
//                )
//                .bodyToMono(String.class)
//                .timeout(REQUEST_TIMEOUT)
//
//                .retryWhen(
//                        Retry.backoff(MAX_RETRIES, RETRY_BACKOFF)
//                                .filter(throwable ->
//                                        throwable instanceof GeminiRateLimitException
//                                )
//                                .doBeforeRetry(signal ->
//                                        log.warn("Gemini 429 — retry attempt #{}", signal.totalRetries() + 1)
//                                )
//                )
//
//                // LOG RESPONSE TIME
//                .doOnSuccess(res -> {
//                    long timeTaken = System.currentTimeMillis() - start;
//                    log.info("Gemini responded in {} ms", timeTaken);
//                })
//
//                // HANDLE ERRORS CLEANLY
//                .doOnError(err ->
//                        log.error("Gemini call failed: {}", err.getMessage())
//                );
//    }
//
//    public static class GeminiRateLimitException extends RuntimeException {
//        public GeminiRateLimitException(String msg) {
//            super(msg);
//        }
//    }
//}
