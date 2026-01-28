package com.saif.fitness.aiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
public class AiserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiserviceApplication.class, args);
	}

}
