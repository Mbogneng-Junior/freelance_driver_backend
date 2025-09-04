package com.freelance.driver_backend.dto.onboarding;

import lombok.Data;

@Data
public class DriverOnboardingRequest {
    // Infos pour le compte User
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String phoneNumber;

    // Infos pour l'organisation
    private String companyName;
    private String companyDescription;

    // Infos pour le profil spécifique Driver
    private String licenseNumber;
    private String vehicleDetails;
    private String otp;
}