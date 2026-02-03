package com.saif.fitness.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupResponse {

    private String keycloakId;
    private String email;
    private String firstName;
    private String lastName;
    private String message;
    private boolean success;
}