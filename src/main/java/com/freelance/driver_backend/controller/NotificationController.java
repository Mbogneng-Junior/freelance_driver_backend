package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.model.DeviceToken;
import com.freelance.driver_backend.repository.DeviceTokenRepository;
import com.freelance.driver_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final DeviceTokenRepository deviceTokenRepository;

    @PostMapping("/register-device")
    public Mono<ResponseEntity<Void>> registerDevice(
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestBody Map<String, String> payload) {
        
        String token = payload.get("token");
        if (token == null || token.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return jwtMono.flatMap(jwt -> {
            DeviceToken deviceToken = new DeviceToken();
            deviceToken.setUserId(JwtUtil.getUserIdFromToken(jwt));
            deviceToken.setToken(token);
            
            log.info("Enregistrement du token de l'appareil pour l'utilisateur {}", deviceToken.getUserId());
            
            return deviceTokenRepository.save(deviceToken)
                .thenReturn(new ResponseEntity<Void>(HttpStatus.CREATED));
        });
    }
}