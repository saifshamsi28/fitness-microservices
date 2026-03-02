package com.saif.fitness.userservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "user-service",
                "ts", Instant.now().toString()
        ));
    }
}
