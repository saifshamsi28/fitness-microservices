package com.saif.fitness.activityservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ActivityserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ActivityserviceApplication.class, args);
	}

//	@Bean
//	CommandLineRunner printMongo(@Value("${spring.data.mongodb.uri:NOT_FOUND}") String uri) {
//		return args -> System.out.println("🔥 Mongo URI IN USE = " + uri);
//	}

//	@Autowired
//	private MongoTemplate mongoTemplate;
//
//	@PostConstruct
//	public void forceCreateDb() {
//		mongoTemplate.createCollection("force_test");
//		System.out.println("✅ force_test collection created");
//	}

}
