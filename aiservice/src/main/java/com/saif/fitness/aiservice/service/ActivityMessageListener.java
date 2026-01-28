//package com.saif.fitness.aiservice.service;
//
//import com.saif.fitness.aiservice.model.Activity;
//import com.saif.fitness.aiservice.model.Recommendation;
//import com.saif.fitness.aiservice.repository.RecommendationRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.stereotype.Service;
//
//@Service
//@Slf4j
//@RequiredArgsConstructor
//public class ActivityMessageListener {
//
//    private final ActivityAIService activityAIService;
//    private final RecommendationRepository recommendationRepository;
//
//    @KafkaListener(topics = "${kafka.topic.name}", groupId = "activity-processor-group")
//    public void processActivity(Activity activity) {
//        try {
//            log.info("Received activity for processing: User Id {}",activity.getUserId());
//            log.info("Received activity for processing: Activity Type {}",activity.getActivityType());
//            Recommendation aiRecommendation=activityAIService.generateRecommendation(activity);
//            recommendationRepository.save(aiRecommendation);
//            Thread.sleep(40000);
//        } catch (Exception e) {
//            log.error("AI failed, skipping to avoid infinite retry", e);
//            // swallow exception so Kafka commits offset
//        }
//    }
//}

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
import org.springframework.transaction.annotation.Transactional;

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
//    @Transactional
    public void processActivity(
            @Payload Activity activity,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.OFFSET) Long offset,
            Acknowledgment acknowledgment) {

        try {
            log.info("Processing activity - UserId: {}", activity.getUserId());

            // 1. Idempotency Check
            if (recommendationRepository.existsByActivityIdAndUserId(activity.getId(), activity.getUserId())) {
                log.info("Already processed. Skipping.");
                acknowledgment.acknowledge();
                return;
            }

            // 2. Call AI (This will sleep automatically now)
            Recommendation aiRecommendation = activityAIService.generateRecommendation(activity);
            recommendationRepository.save(aiRecommendation);

            // 3. Commit
            acknowledgment.acknowledge();
            log.info("Successfully processed activity: {}", activity.getId());

        } catch (Exception e) {
            log.error("Failed to process activity {}. Saving fallback.", activity.getId(), e);

            // 4. FALLBACK (Critical: Save something so we don't lose data)
            try {
                Recommendation fallback = activityAIService.createFallbackRecommendation(activity);
                recommendationRepository.save(fallback);
                acknowledgment.acknowledge(); // Commit the fallback!
                log.info("Fallback saved and offset committed.");
            } catch (Exception ex) {
                log.error("Could not save fallback", ex);
                acknowledgment.acknowledge(); // Commit anyway to unblock the queue
            }
        }
    }
}