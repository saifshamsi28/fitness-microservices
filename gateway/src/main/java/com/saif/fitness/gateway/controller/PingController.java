package com.saif.fitness.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight public endpoint used by UptimeRobot / cron-job.org
 * to keep the service awake on Render free tier.
 * Hit GET /ping → 200 {"status":"ok","ts":"..."}
 */
@RestController
public class PingController {

    @GetMapping("/ping")
    public Mono<ResponseEntity<Map<String, String>>> ping() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "gateway",
                "ts", Instant.now().toString()
        )));
    }
}
