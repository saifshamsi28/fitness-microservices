package com.saif.fitness.userservice.controller;

import com.saif.fitness.userservice.dto.UserRequestDto;
import com.saif.fitness.userservice.dto.UserResponseDto;
import com.saif.fitness.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDto> getUser(@PathVariable String userId){
        return ResponseEntity.ok(userService.getUser(userId));
    }


    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody UserRequestDto userRequestDto){
        return ResponseEntity.ok(userService.register(userRequestDto));
    }

    @GetMapping("/{userId}/validate")
    public ResponseEntity<Boolean> validateUser(@PathVariable String userId){
        return ResponseEntity.ok(userService.existsByUserId(userId));
    }
}
