package com.saif.fitness.activityservice.service;

import com.saif.fitness.activityservice.dto.ActivityRequest;
import com.saif.fitness.activityservice.dto.ActivityResponse;
import com.saif.fitness.activityservice.exception.UserNotFoundException;
import com.saif.fitness.activityservice.models.Activity;
import com.saif.fitness.activityservice.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final UserValidationService userValidationService;

    public ActivityResponse trackActivity(ActivityRequest request) {

        boolean isValid=userValidationService.validateUser(request.getUserId());

        if(!isValid){
            throw new UserNotFoundException("User not exists with id: "+request.getUserId());
        }

        Activity activity=Activity.builder()
                .userId(request.getUserId())
                .activityType(request.getActivityType())
                .duration(request.getDuration())
                .startTime(request.getStartTime())
                .caloriesBurned(request.getCaloriesBurned())
                .additionalMetrics(request.getAdditionalMetrics())
                .build();

        activity=activityRepository.save(activity);

        return mapToResponse(activity);
    }

    private ActivityResponse mapToResponse(Activity activity){
        return ActivityResponse.builder()
                .id(activity.getId())
                .userId(activity.getUserId())
                .activityType(activity.getActivityType())
                .duration(activity.getDuration())
                .startTime(activity.getStartTime())
                .caloriesBurned(activity.getCaloriesBurned())
                .additionalMetrics(activity.getAdditionalMetrics())
                .createdAt(activity.getCreatedAt())
                .updatedAt(activity.getUpdatedAt())
                .build();
    }
}
