package com.saif.fitness.aiservice.controller;

import com.saif.fitness.aiservice.model.Activity;
import com.saif.fitness.aiservice.model.Recommendation;
import com.saif.fitness.aiservice.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Recommendation>> getUserRecommendation(@PathVariable String userId) {
        return ResponseEntity.ok(recommendationService.getUserRecommendations(userId));
    }

    @GetMapping("/activity/{activityId}")
    public ResponseEntity<Recommendation> getActivityRecommendation(@PathVariable String activityId) {
        return ResponseEntity.ok(recommendationService.getRecommendation(activityId));
    }

    /**
     * Backfill endpoint for activities whose Kafka events were already consumed
     * but whose recommendations were never saved (due to MongoDB being unavailable).
     *
     * Usage: POST /api/recommendations/backfill
     * Body: JSON array of Activity objects (copy from GET /api/activities response)
     *
     * Idempotent — safe to call multiple times.
     */
    @PostMapping("/backfill")
    public ResponseEntity<List<Recommendation>> backfill(@RequestBody List<Activity> activities) {
        List<Recommendation> results = recommendationService.backfill(activities);
        return ResponseEntity.ok(results);
    }
}
