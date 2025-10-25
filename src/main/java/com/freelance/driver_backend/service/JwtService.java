package com.freelance.driver_backend.service;

import com.freelance.driver_backend.model.mock.MockUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;

    public String generateToken(MockUser user) {
        Instant now = Instant.now();
        Instant expiration = now.plus(1, ChronoUnit.HOURS);

        // Créer le claim "user" compatible avec votre JwtUtil
        Map<String, Object> userClaim = new HashMap<>();
        userClaim.put("id", user.getId().toString());
        userClaim.put("username", user.getUsername());
        userClaim.put("email", user.getEmail());
        userClaim.put("firstName", user.getFirstName());
        userClaim.put("lastName", user.getLastName());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("freelance-driver-app")
                .issuedAt(now)
                .expiresAt(expiration)
                .subject(user.getUsername())
                .claim("user", userClaim)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}