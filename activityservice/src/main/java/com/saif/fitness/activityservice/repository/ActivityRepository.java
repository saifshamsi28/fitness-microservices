package com.saif.fitness.activityservice.repository;

import com.saif.fitness.activityservice.models.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;

public interface ActivityRepository extends MongoRepository<Activity, String> {

    Page<Activity> findByUserId(String userId, Pageable pageable);

}
