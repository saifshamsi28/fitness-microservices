package com.saif.fitness.userservice.repository;

import com.saif.fitness.userservice.models.OtpState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OtpStateRepository extends JpaRepository<OtpState, Long> {

    /**
     * Acquires a PESSIMISTIC_WRITE (SELECT … FOR UPDATE) lock.
     * Used in generateOtp() to make the quota check-and-increment atomic,
     * preventing two concurrent requests from both passing the limit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM OtpState s WHERE s.email = :email AND s.otpType = :otpType")
    Optional<OtpState> findByEmailAndOtpTypeForUpdate(
            @Param("email")   String email,
            @Param("otpType") String otpType);

    Optional<OtpState> findByEmailAndOtpType(String email, String otpType);
}
