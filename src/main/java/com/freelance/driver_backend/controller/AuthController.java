package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.external.LoginRequest;
import com.freelance.driver_backend.dto.external.NotificationRequest;
import com.freelance.driver_backend.dto.external.RegistrationRequest; // Garder cet import
import com.freelance.driver_backend.dto.onboarding.OnboardingResponse;
import com.freelance.driver_backend.model.OtpVerification; // Garder cet import
import com.freelance.driver_backend.repository.OtpVerificationRepository; // Garder cet import
import com.freelance.driver_backend.service.LoginService;
import com.freelance.driver_backend.service.external.AuthService; // Garder cet import
import com.freelance.driver_backend.service.external.NotificationService;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value; // Garder cet import
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
@RequestMapping("/api") // Le mapping doit être "/api" pour que /api/register fonctionne
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final LoginService loginService;
    private final OtpVerificationRepository otpVerificationRepository; // Réactiver l'injection
    private final NotificationService notificationService;
    private final Dotenv dotenv;
    private final AuthService authService; // Réactiver l'injection

    @Value("${freelancedriver.oauth2.client-id}")
    private String oauthClientId;
    @Value("${freelancedriver.oauth2.client-secret}")
    private String oauthClientSecret;


    @PostMapping("/auth/login") // La route de login reste sous /api/auth/login
    public Mono<ResponseEntity<OnboardingResponse>> login(@RequestBody LoginRequest loginRequest) {
        return loginService.loginAndGetContext(loginRequest)
                .doOnNext(response -> {
                    log.info("▶️ Backend DEBUG: Réponse Login envoyée au frontend: {}", response);
                })
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.status(401).build());
    }

    /**
     * Gère l'inscription initiale : enregistre l'utilisateur via l'API externe,
     * puis génère et envoie l'OTP.
     * Cet endpoint est appelé par `SignUp.tsx`.
     */
    /* @PostMapping("/register") // CETTE ROUTE DOIT ÊTRE RÉACTIVÉE
    public Mono<ResponseEntity<Map<String, String>>> registerUserAndInitiateOtp(@RequestBody RegistrationRequest request) {
        String email = request.getEmail();
        String firstName = request.getFirstName();
        
        log.info("▶️ Début du processus d'inscription (API externe) et OTP pour l'email: {}", email);
        
        // 1. Obtenir le token M2M (Machine-to-Machine)
        return authService.getClientCredentialsToken(oauthClientId, oauthClientSecret)
            .flatMap(m2mTokenResponse -> {
                String m2mBearerToken = "Bearer " + m2mTokenResponse.getAccessToken();

                // 2. Enregistrer l'utilisateur auprès du service d'authentification externe réel
                return authService.registerUser(request, m2mBearerToken)
                    .flatMap(userDto -> {
                        log.info("✅ Utilisateur '{}' enregistré avec succès via l'API externe. ID: {}", userDto.getEmail(), userDto.getId());
                        
                        // 3. Générer et sauvegarder l'OTP localement
                        String otp = String.format("%06d", new Random().nextInt(999999));
                        OtpVerification newVerification = new OtpVerification();
                        newVerification.setEmail(email);
                        newVerification.setOtpCode(otp);
                        newVerification.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
                        
                        return otpVerificationRepository.save(newVerification);
                    })
                    .flatMap(savedOtp -> {
                        log.info("✅ OTP {} sauvegardé localement pour {}", savedOtp.getOtpCode(), email);

                        // 4. Envoyer l'email OTP via le service de notification (mock ou réel, selon le profil)
                        UUID otpTemplateId = UUID.fromString(dotenv.get("TEMPLATE_EMAIL_OTP_ID"));
                        UUID tempOrgId = UUID.fromString(dotenv.get("SYSTEM_ORGANIZATION_ID"));

                        NotificationRequest otpRequest = NotificationRequest.builder()
                            .templateId(otpTemplateId)
                            .recipients(List.of(email))
                            .metadata(Map.of("firstName", firstName, "otpCode", savedOtp.getOtpCode()))
                            .build();
                        
                        return notificationService.sendEmailNotification(tempOrgId, otpRequest, null, null);
                    })
                    .map(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            log.info("✅ Email OTP envoyé avec succès à {}", email);
                            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Utilisateur enregistré, OTP envoyé."));
                        } else {
                            log.error("❌ Échec de l'envoi de l'email OTP à {}", email);
                            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("message", "Utilisateur enregistré, mais l'email OTP n'a pas pu être envoyé."));
                        }
                    })
                    .onErrorResume(RuntimeException.class, e -> {
                        log.error("❌ Erreur lors de l'inscription ou de l'envoi de l'OTP pour {}: {}", email, e.getMessage());
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage())));
                    });
            });
    } */

        @PostMapping("/register") // CETTE ROUTE DOIT ÊTRE RÉACTIVÉE
    public Mono<ResponseEntity<Map<String, String>>> registerUserAndInitiateOtp(@RequestBody RegistrationRequest request) {
        String email = request.getEmail();
        String firstName = request.getFirstName();
        
        log.info("▶️ Début du processus d'inscription (API externe) et OTP pour l'email: {}", email);
        
        // Le token M2M n'est plus requis pour l'enregistrement de l'utilisateur.
        // L'appel à getClientCredentialsToken est donc supprimé ici.
        String m2mBearerToken = "Bearer BQC5Zt6s9y$C&F)J@NcRfUjXn2r5u8x/";
        // 1. Enregistrer l'utilisateur auprès du service d'authentification externe.
        return authService.registerUser(request,m2mBearerToken)
            .flatMap(userDto -> {
                log.info("✅ Utilisateur '{}' enregistré avec succès via l'API externe. ID: {}", userDto.getEmail(), userDto.getId());
                
                // 2. Générer et sauvegarder l'OTP localement
                String otp = String.format("%06d", new Random().nextInt(999999));
                OtpVerification newVerification = new OtpVerification();
                newVerification.setEmail(email);
                newVerification.setOtpCode(otp);
                
                newVerification.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
                
                return otpVerificationRepository.save(newVerification);
            })
            .flatMap(savedOtp -> {
                log.info("✅ OTP {} sauvegardé localement pour {}", savedOtp.getOtpCode(), email);

                // 3. Envoyer l'email OTP via le service de notification
                UUID otpTemplateId = UUID.fromString(dotenv.get("TEMPLATE_EMAIL_OTP_ID"));
                UUID tempOrgId = UUID.fromString(dotenv.get("SYSTEM_ORGANIZATION_ID"));

                NotificationRequest otpRequest = NotificationRequest.builder()
                    .templateId(otpTemplateId)
                    .recipients(List.of(email))
                    .metadata(Map.of("firstName", firstName, "otpCode", savedOtp.getOtpCode()))
                    .build();
                
                // Note: L'envoi de notification pourrait nécessiter un token M2M.
                // Si c'est le cas, l'appel à getClientCredentialsToken devrait être replacé ici.
                return notificationService.sendEmailNotification(tempOrgId, otpRequest, null, null);
            })
            .map(success -> {
                if (Boolean.TRUE.equals(success)) {
                    log.info("✅ Email OTP envoyé avec succès à {}", email);
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Utilisateur enregistré, OTP envoyé."));
                } else {
                    log.error("❌ Échec de l'envoi de l'email OTP à {}", email);
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("message", "Utilisateur enregistré, mais l'email OTP n'a pas pu être envoyé."));
                }
            })
            .onErrorResume(RuntimeException.class, e -> {
                log.error("❌ Erreur lors de l'inscription ou de l'envoi de l'OTP pour {}: {}", email, e.getMessage());
                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage())));
            });
    }
}