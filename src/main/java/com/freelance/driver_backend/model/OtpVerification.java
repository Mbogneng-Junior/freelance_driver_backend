package com.freelance.driver_backend.model;

import lombok.Data;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

@Table("otp_verifications")
@Data
public class OtpVerification {
    @PrimaryKey // L'email de l'utilisateur est la clé unique
    private String email;
    
    private String otpCode;
    
    private Instant expiresAt;
}