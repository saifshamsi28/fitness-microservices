package com.saif.fitness.aiservice.service;

import com.saif.fitness.aiservice.model.Activity;
import com.saif.fitness.aiservice.model.Recommendation;
import com.saif.fitness.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final ActivityAIService activityAIService;

    public List<Recommendation> getUserRecommendations(String userId) {
        return recommendationRepository.findByUserId(userId);
    }

    public Recommendation getRecommendation(String activityId) {
        return recommendationRepository.findByActivityId(activityId)
                .orElseThrow(() -> new RuntimeException("No recommendation found for this activity: " + activityId));
    }

    /**
     * Backfill endpoint — processes activities whose Kafka events were already
     * consumed (offsets committed) before the MongoDB fix, so they were silently
     * dropped and will never be redelivered by Kafka.
     * Idempotent: skips any activity that already has a recommendation.
     */
    public List<Recommendation> backfill(List<Activity> activities) {
        List<Recommendation> results = new ArrayList<>();
        for (Activity activity : activities) {
            try {
                if (recommendationRepository.existsByActivityIdAndUserId(
                        activity.getId(), activity.getUserId())) {
                    log.info("Backfill skip (already exists): {}", activity.getId());
                    recommendationRepository.findByActivityId(activity.getId())
                            .ifPresent(results::add);
                    continue;
                }
                log.info("Backfill generating recommendation for activity {}", activity.getId());
                Recommendation rec = activityAIService.generateRecommendation(activity).block();
                if (rec != null) {
                    results.add(recommendationRepository.save(rec));
                    log.info("Backfill saved recommendation for activity {}", activity.getId());
                }
            } catch (Exception e) {
                log.error("Backfill failed for activity {}", activity.getId(), e);
            }
        }
        return results;
    }
}
