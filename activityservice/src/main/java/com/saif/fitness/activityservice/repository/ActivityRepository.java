package com.saif.fitness.activityservice.repository;

import com.saif.fitness.activityservice.models.Activity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ActivityRepository extends MongoRepository<Activity, String> {

}
