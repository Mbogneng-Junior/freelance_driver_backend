package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.external.NotificationRequest;
import com.freelance.driver_backend.dto.external.RegistrationRequest;
import com.freelance.driver_backend.model.OtpVerification;
import com.freelance.driver_backend.repository.OtpVerificationRepository;
import com.freelance.driver_backend.service.external.NotificationService;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
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

/**
 * Ce contrôleur simule les endpoints d'authentification qui ne sont pas gérés
 * par le service d'onboarding principal. Il est actif uniquement en profil de développement.
 * Son rôle principal est de gérer l'initiation de l'inscription et l'envoi de l'OTP.
 */
@RestController
@Profile("!dev-resource-mock")
@RequiredArgsConstructor
@Slf4j
public class AuthMockController {

    private final OtpVerificationRepository otpVerificationRepository;
    private final NotificationService notificationService;
    private final Dotenv dotenv;

    @PostMapping("/api/register") // Ce mapping est maintenant unique quand dev-auth-mock est actif
    public Mono<ResponseEntity<Map<String, String>>> initiateRegistration(@RequestBody RegistrationRequest request) {
        // ... (votre implémentation existante de initiateRegistration)
        String email = request.getEmail();
        String firstName = request.getFirstName();
        
        if (email == null || firstName == null) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("message", "Email and firstName are required.")));
        }

        log.info("▶️ Début du processus d'inscription OTP pour l'email: {}", email);

        String otp = String.format("%06d", new Random().nextInt(999999));
        
        OtpVerification newVerification = new OtpVerification();
        newVerification.setEmail(email);
        newVerification.setOtpCode(otp);
        newVerification.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES)); 

        return otpVerificationRepository.save(newVerification)
            .flatMap(savedOtp -> {
                log.info("✅ OTP {} sauvegardé pour {}", otp, email);

                UUID otpTemplateId = UUID.fromString(dotenv.get("TEMPLATE_EMAIL_OTP_ID"));
                UUID tempOrgId = UUID.fromString(dotenv.get("SYSTEM_ORGANIZATION_ID"));

                NotificationRequest otpEmailRequest = NotificationRequest.builder()
                    .templateId(otpTemplateId)
                    .recipients(List.of(email))
                    .metadata(Map.of("firstName", firstName, "otpCode", otp))
                    .build();
                
                return notificationService.sendEmailNotification(tempOrgId, otpEmailRequest, null, null)
                    .map(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            log.info("✅ Email OTP envoyé avec succès à {}", email);
                            return ResponseEntity.status(HttpStatus.CREATED)
                                                 .body(Map.of("message", "User registration initiated. Please check your email for OTP."));
                        } else {
                            log.error("❌ Échec de l'envoi de l'email OTP à {}", email);
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                 .body(Map.of("message", "Could not send OTP email."));
                        }
                    });
            })
            .onErrorResume(error -> {
                log.error("❌ Erreur lors de la sauvegarde de l'OTP pour {}", email, error);
                return Mono.just(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                  .body(Map.of("message", "Failed to save OTP record."))
                );
            });
    }

    @GetMapping("/api/ping-auth-mock")
    public Mono<String> ping() {
        log.info("✅ AuthMockController est bien actif et a reçu un ping !");
        return Mono.just("Pong from AuthMockController! Le profil dev-auth-mock est bien chargé.");
    }
}