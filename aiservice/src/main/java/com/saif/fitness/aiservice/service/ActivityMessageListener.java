package com.saif.fitness.aiservice.service;

import com.saif.fitness.aiservice.model.Activity;
import com.saif.fitness.aiservice.model.Recommendation;
import com.saif.fitness.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityMessageListener {

    private final ActivityAIService activityAIService;
    private final RecommendationRepository recommendationRepository;

    @KafkaListener(topics = "${kafka.topic.name}", groupId = "activity-processor-group")
    public void processActivity(Activity activity) {
        try {
            log.info("Received activity for processing: User Id {}",activity.getUserId());
            log.info("Received activity for processing: Activity Type {}",activity.getActivityType());
            Recommendation aiRecommendation=activityAIService.generateRecommendation(activity);
            recommendationRepository.save(aiRecommendation);
        } catch (Exception e) {
            log.error("AI failed, skipping to avoid infinite retry", e);
            // swallow exception so Kafka commits offset
        }
    }
}
