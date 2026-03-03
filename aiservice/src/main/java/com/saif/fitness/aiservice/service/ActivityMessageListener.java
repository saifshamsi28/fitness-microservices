package com.saif.fitness.aiservice.service;

import com.saif.fitness.aiservice.model.Activity;
import com.saif.fitness.aiservice.model.Recommendation;
import com.saif.fitness.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityMessageListener {

    private final ActivityAIService activityAIService;
    private final RecommendationRepository recommendationRepository;

    @KafkaListener(
            topics = "${kafka.topic.name}",
            groupId = "activity-processor-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void processActivity(
            @Payload Activity activity,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.OFFSET) Long offset,
            Acknowledgment acknowledgment) {

        log.info("Processing activity - UserId: {}", activity.getUserId());

        // 1. Idempotency Check
        if (recommendationRepository.existsByActivityIdAndUserId(
                activity.getId(), activity.getUserId())) {

            log.info("Already processed. Skipping.");
            acknowledgment.acknowledge();
            return;
        }

        // 2. Generate recommendation and only commit offset AFTER successful save.
        // Previously, acknowledge() was in doFinally (fires on error too) — that caused
        // offsets to be committed even when MongoDB was down, losing those activities
        // permanently. Now: ack only happens inside doOnNext (success path).
        activityAIService.generateRecommendation(activity)
                .doOnNext(rec -> {
                    recommendationRepository.save(rec);
                    acknowledgment.acknowledge();
                    log.info("Recommendation saved and offset committed for activity {}", activity.getId());
                })
                .doOnError(e -> log.error(
                        "Failed to process activity {} — offset NOT committed, will retry on next restart",
                        activity.getId(), e))
                .subscribe();
    }
}