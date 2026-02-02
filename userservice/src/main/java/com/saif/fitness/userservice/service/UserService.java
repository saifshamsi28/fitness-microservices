package com.saif.fitness.userservice.service;

import com.saif.fitness.userservice.dto.UserRequestDto;
import com.saif.fitness.userservice.dto.UserResponseDto;
import com.saif.fitness.userservice.exception.EmailAlreadyExistsException;
import com.saif.fitness.userservice.models.User;
import com.saif.fitness.userservice.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    public UserResponseDto register(UserRequestDto userRequest) {
        log.info("In USER-SERVICE/UserService/register, request: {}",userRequest);
        User user=new User();
        if(userRepository.existsByEmail(userRequest.getEmail())){
            user=userRepository.findByEmail(userRequest.getEmail());

            UserResponseDto responseDto= UserResponseDto.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .keycloakId(user.getKeycloakId())
                    .createdAt(user.getCreatedAt())
                    .updatedAt(user.getUpdatedAt())
                    .build();

            log.info("In USER-SERVICE/UserService/existByEmail check, response: {}",responseDto);
            return responseDto;
        }

        user.setEmail(userRequest.getEmail());
        user.setFirstName(userRequest.getFirstName());
        user.setLastName(userRequest.getLastName());
        user.setPassword(userRequest.getPassword());
        user.setKeycloakId(userRequest.getKeycloakId());

        user=userRepository.save(user);

        UserResponseDto userResponseDto= UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .keycloakId(user.getKeycloakId())
                .build();
        log.info("In USER-SERVICE/UserService/ not exists by email check, response: {}",userResponseDto);
        return userResponseDto;
    }

    public UserResponseDto getUser(String userId) {
        User user=userRepository.findByKeycloakId(userId).orElseThrow(
                ()->new EntityNotFoundException("User not found with id: "+userId));

        return UserResponseDto.builder()
                .id(userId)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .createdAt(user.getCreatedAt())
                .keycloakId(user.getKeycloakId())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public Boolean existsByKeYCloakUserId(String keycloakId) {
        return userRepository.existsByKeycloakId(keycloakId);
    }
}
