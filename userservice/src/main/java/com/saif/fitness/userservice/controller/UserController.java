package com.saif.fitness.userservice.controller;

import com.saif.fitness.userservice.dto.UserResponseDto;
import com.saif.fitness.userservice.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDto> getUser(@PathVariable String userId) {
        System.err.println("fetching user with id: " + userId);
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @GetMapping("/{userId}/validate")
    public ResponseEntity<Boolean> validateUser(@PathVariable String userId, HttpServletRequest httpServletRequest) {
        System.out.println("incoming request: " + httpServletRequest.getRequestURI());
        return ResponseEntity.ok(userService.existsByKeYCloakUserId(userId));
    }
}
