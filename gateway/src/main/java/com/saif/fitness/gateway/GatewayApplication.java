package com.saif.fitness.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootApplication
public class GatewayApplication {

	@Autowired
	private RouteLocator routeLocator;

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void printRoutes() {
		System.out.println("========================================");
		System.out.println("LOADED GATEWAY ROUTES:");
		System.out.println("========================================");
		routeLocator.getRoutes().subscribe(route ->
				System.out.println("Route ID: " + route.getId() +
						" | URI: " + route.getUri() +
						" | Predicate: " + route.getPredicate()));
		System.out.println("========================================");
	}
}