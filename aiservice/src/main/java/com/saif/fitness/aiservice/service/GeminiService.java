////package com.saif.fitness.aiservice.service;
////
////import org.slf4j.Logger;
////import org.slf4j.LoggerFactory;
////import org.springframework.beans.factory.annotation.Value;
////import org.springframework.http.MediaType;
////import org.springframework.stereotype.Service;
////import org.springframework.web.reactive.function.client.WebClient;
////import reactor.core.publisher.Mono;
////
////import java.util.Map;
////
////@Service
////public class GeminiService {
////
////    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
////    private  final WebClient webClient;
////
////    @Value("${gemini.api.url}")
////    private String geminiApiUrl;
////
////    @Value("${gemini.api.key}")
////    private String geminiApiKey;
////
////    public GeminiService(WebClient.Builder webClientBuilder) {
////        this.webClient = webClientBuilder.build();
////    }
////
////    public String getRecommendations(String details){
////        Map<String, Object> requestBody=Map.of(
////                "contents", new Object[]{
////                        Map.of("parts", new Object[]{
////                                Map.of("text", details)
////                        })
////                }
////        );
////
////
////        return webClient.post()
////                .uri(geminiApiUrl)
////                .contentType(MediaType.APPLICATION_JSON)
////                .header("X-goog-api-key", geminiApiKey)
////                .bodyValue(requestBody)
////                .retrieve()
////                .onStatus(
////                        status -> status.value() == 429,
////                        response -> Mono.error(new RuntimeException("Gemini rate limit exceeded"))
////                )
////                .bodyToMono(String.class)
////                .block();
////    }
////}
//
package com.saif.fitness.aiservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class GeminiService {

    private final WebClient webClient;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    // 1. TIMING CONTROL
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    // 5 seconds gap to be absolutely safe (12 requests/min)
    private static final long MIN_REQUEST_GAP_MS = 5000;

    public GeminiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String getRecommendations(String details) {
        // 2. ENFORCE SLEEP BEFORE REQUEST
        enforceRateLimit();

        Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", details)
                        })
                }
        );

        log.info("Sending request to Gemini...");

        return webClient.post()
                .uri(geminiApiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-goog-api-key", geminiApiKey)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                        status -> status.value() == 429,
                        response -> Mono.error(new GeminiRateLimitException("Gemini 429 Too Many Requests"))
                )
                .onStatus(
                        status -> status.is5xxServerError(),
                        response -> Mono.error(new RuntimeException("Gemini Server Error"))
                )
                .bodyToMono(String.class)
                // 3. RETRY LOGIC
                .retryWhen(
                        Retry.backoff(3, Duration.ofSeconds(10)) // Wait 10s before retry
                                .filter(throwable -> throwable instanceof GeminiRateLimitException)
                                .doBeforeRetry(signal -> log.warn("Rate limit hit! Retrying attempt #{}", signal.totalRetries() + 1))
                )
                .block();
    }

    private void enforceRateLimit() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime.get();

        if (timeSinceLastRequest < MIN_REQUEST_GAP_MS) {
            long sleepTime = MIN_REQUEST_GAP_MS - timeSinceLastRequest;
            try {
                log.info("Creating safe gap. Sleeping for {} ms.", sleepTime);
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime.set(System.currentTimeMillis());
    }

    public static class GeminiRateLimitException extends RuntimeException {
        public GeminiRateLimitException(String msg) { super(msg); }
    }
}