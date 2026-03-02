package com.saif.fitness.userservice.dto;

import com.saif.fitness.userservice.models.enums.UserRole;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponseDto {
    private String id;
    private String keycloakId;
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
