package com.saif.fitness.activityservice.controller;

import com.saif.fitness.activityservice.dto.ActivityRequest;
import com.saif.fitness.activityservice.dto.ActivityResponse;
import com.saif.fitness.activityservice.models.Activity;
import com.saif.fitness.activityservice.models.enums.ActivityType;
import com.saif.fitness.activityservice.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @PostMapping("/track")
    public ResponseEntity<ActivityResponse> trackActivity(@RequestBody ActivityRequest activityRequest){
        return ResponseEntity.ok(activityService.trackActivity(activityRequest));
    }
}
