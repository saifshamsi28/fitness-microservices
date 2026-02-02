package com.saif.fitness.activityservice.controller;

import com.saif.fitness.activityservice.dto.ActivityRequest;
import com.saif.fitness.activityservice.dto.ActivityResponse;
import com.saif.fitness.activityservice.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @PostMapping("/track")
    public ResponseEntity<ActivityResponse> trackActivity(@RequestBody ActivityRequest activityRequest){
        return ResponseEntity.ok(activityService.trackActivity(activityRequest));
    }

    @GetMapping
    public ResponseEntity<List<ActivityResponse>> getActivities(
            @RequestParam int page,
            @RequestParam int size,
            @RequestParam String userId
    ) {
        return ResponseEntity.ok(
                activityService.getActivities(page, size, userId)
        );
    }

}
