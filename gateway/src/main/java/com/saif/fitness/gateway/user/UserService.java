package com.saif.fitness.gateway.user;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final WebClient userServiceWebClient;

    public Mono<Boolean> validateUser(String userId){
            return userServiceWebClient.get()
                    .uri("/api/users/{userId}/validate", userId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .onErrorResume(WebClientResponseException.class, e -> {
                        if (e.getStatusCode() == HttpStatus.NOT_FOUND)
                            return Mono.error(new RuntimeException("User not found: "+userId));
                        else if (e.getStatusCode() == HttpStatus.BAD_REQUEST)
                            return Mono.error(new RuntimeException("Invalid user id: "+userId));
                        return Mono.error(new RuntimeException("Unexpected error: "+userId));
                    });
    }


    public Mono<UserResponseDto> registerUser(UserRequestDto userRequestDto) {
        log.info("calling user registration api: {}",userRequestDto.getEmail());
        return userServiceWebClient.post()
                .uri("/api/users/register")
                .bodyValue(userRequestDto)
                .retrieve()
                .bodyToMono(UserResponseDto.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                   if (e.getStatusCode() == HttpStatus.BAD_REQUEST)
                        return Mono.error(new RuntimeException("Bad request: "+e.getMessage()));
                    return Mono.error(new RuntimeException("Unexpected error: "+e.getMessage()));
                });
    }
}
