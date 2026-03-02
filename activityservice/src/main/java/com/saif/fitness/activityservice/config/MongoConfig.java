package com.saif.fitness.activityservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

// Spring Boot auto-configures MongoDB from spring.data.mongodb.uri (set via MONGO_ACTIVITY_URI env var)
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
