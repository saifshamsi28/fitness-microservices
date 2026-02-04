package com.saif.fitness.activityservice.service;

import com.saif.fitness.activityservice.dto.ActivityRequest;
import com.saif.fitness.activityservice.dto.ActivityResponse;
import com.saif.fitness.activityservice.exception.UserNotFoundException;
import com.saif.fitness.activityservice.models.Activity;
import com.saif.fitness.activityservice.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);
    private final ActivityRepository activityRepository;
    private final UserValidationService userValidationService;
    private final KafkaTemplate<String, Activity> kafkaTemplate;

    @Value("${kafka.topic.name}")
    private String topicName;

    public ActivityResponse trackActivity(ActivityRequest request) {

        log.info("In ACTIVITY-SERVICE/ActivityService/trackActivity, request: {}",request);

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

        try {
            kafkaTemplate.send(topicName,activity.getUserId(),activity)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            System.err.println("Kafka send failed: " + ex.getMessage());
                        }
                    });
        }catch (Exception e){
            e.printStackTrace();
        }

        return mapToResponse(activity);
    }

    private ActivityResponse mapToResponse(Activity activity){

        ActivityResponse response= ActivityResponse.builder()
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
        log.info("In ACTIVITY-SERVICE/ActivityService/mapToResponse, response: {}",response);
        return response;
    }

    public List<ActivityResponse> getActivities(int page, int size, String userId) {

        PageRequest pageRequest = PageRequest.of(page, size);

        return activityRepository
                .findByUserId(userId, pageRequest)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

}
