package com.saif.fitness.userservice.repository;

import com.saif.fitness.userservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByEmail(String email);

    Boolean existsByKeycloakId(String keycloakId);
    Optional<User> findByKeycloakId(String keycloakId);

    User findByEmail(String email);
}