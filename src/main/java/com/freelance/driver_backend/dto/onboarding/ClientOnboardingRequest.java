package com.freelance.driver_backend.dto.onboarding;

import lombok.Data;

@Data
public class ClientOnboardingRequest {
    // Infos pour le compte User
    private String email;
    private String password;
    private String firstName; // Nom du contact principal
    private String lastName;  // Prénom du contact principal
    private String phoneNumber;

    // Infos pour l'organisation du Client
    private String companyName;
    private String companyDescription;
    private String otp;
}