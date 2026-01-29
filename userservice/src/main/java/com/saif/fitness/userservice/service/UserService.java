package com.saif.fitness.userservice.service;

import com.saif.fitness.userservice.dto.UserRequestDto;
import com.saif.fitness.userservice.dto.UserResponseDto;
import com.saif.fitness.userservice.exception.EmailAlreadyExistsException;
import com.saif.fitness.userservice.models.User;
import com.saif.fitness.userservice.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponseDto register(UserRequestDto userRequest) {
        User user=new User();
        if(userRepository.existsByEmail(userRequest.getEmail())){
            user=userRepository.findByEmail(userRequest.getEmail());
            return UserResponseDto.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .password(user.getPassword())
                    .keyCloakId(user.getKeycloakId())
                    .createdAt(user.getCreatedAt())
                    .updatedAt(user.getUpdatedAt())
                    .build();
        }

        user.setEmail(userRequest.getEmail());
        user.setFirstName(userRequest.getFirstName());
        user.setLastName(userRequest.getLastName());
        user.setPassword(userRequest.getPassword());
        user.setKeycloakId(userRequest.getKeycloakId());

        user=userRepository.save(user);

        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .password(user.getPassword())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();

    }

    public UserResponseDto getUser(String userId) {
        User user=userRepository.findById(userId).orElseThrow(
                ()->new EntityNotFoundException("User not found with id: "+userId));

        return UserResponseDto.builder()
                .id(userId)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .password(user.getPassword())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();

    }

    public Boolean existsByKeYCloakUserId(String userId) {
        return userRepository.existsByKeycloakId(userId);
    }
}
