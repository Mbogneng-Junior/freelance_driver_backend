/*package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.external.LoginRequest;
import com.freelance.driver_backend.dto.onboarding.OnboardingResponse;
import com.freelance.driver_backend.service.LoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;

    @PostMapping("/login")
    public Mono<ResponseEntity<OnboardingResponse>> login(@RequestBody LoginRequest loginRequest) {
        return loginService.loginAndGetContext(loginRequest)
                .map(ResponseEntity::ok)
                // Si le Mono est vide (login/profil non trouvé), renvoie une erreur 401 Unauthorized
                .defaultIfEmpty(ResponseEntity.status(401).build());
    }
}*/

package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.external.LoginRequest;
import com.freelance.driver_backend.dto.external.NotificationRequest;
import com.freelance.driver_backend.dto.onboarding.OnboardingResponse;
import com.freelance.driver_backend.model.OtpVerification;
import com.freelance.driver_backend.repository.OtpVerificationRepository;
import com.freelance.driver_backend.service.LoginService;
import com.freelance.driver_backend.service.external.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final LoginService loginService;
    private final OtpVerificationRepository otpVerificationRepository;
    private final NotificationService notificationService;

    // Assurez-vous que cet ID est correct et correspond à votre template d'email OTP
    private static final UUID OTP_EMAIL_TEMPLATE_ID = UUID.fromString("99f0fa9f-80bd-4e54-8385-a3e0dee99770"); 
    private static final UUID TEMP_ORGANIZATION_ID = UUID.fromString("73ba467d-9b2e-481a-827e-edbddc4f775d");

    @PostMapping("/login")
    public Mono<ResponseEntity<OnboardingResponse>> login(@RequestBody LoginRequest loginRequest) {
        return loginService.loginAndGetContext(loginRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.status(401).build());
    }

    @PostMapping("/initiate-registration")
    public Mono<ResponseEntity<Map<String, String>>> initiateRegistration(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String firstName = payload.get("firstName");
        
        log.info("▶️ Début du processus d'inscription OTP pour l'email: {}", email);
        
        String otp = String.format("%06d", new Random().nextInt(999999));
        
        OtpVerification newVerification = new OtpVerification();
        newVerification.setEmail(email);
        newVerification.setOtpCode(otp);
        newVerification.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));

        return otpVerificationRepository.save(newVerification)
            .flatMap(saved -> {
                NotificationRequest otpRequest = NotificationRequest.builder()
                    .templateId(OTP_EMAIL_TEMPLATE_ID)
                    .recipients(List.of(email))
                    .metadata(Map.of("firstName", firstName, "otpCode", otp))
                    .build();
                
                return notificationService.sendEmailNotification(TEMP_ORGANIZATION_ID, otpRequest, null, null);
            })
            .map(success -> {
                if (Boolean.TRUE.equals(success)) {
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "OTP sent."));
                }
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Could not send OTP."));
            });
    }
}