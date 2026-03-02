package com.saif.fitness.userservice.dto;

import lombok.Data;

@Data
public class UpdateProfileRequestDto {
    private String firstName;
    private String lastName;
    private String email;
}
