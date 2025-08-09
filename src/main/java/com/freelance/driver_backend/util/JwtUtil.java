package com.freelance.driver_backend.util;

import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Map;
import java.util.UUID;

public class JwtUtil {

    public static UUID getUserIdFromToken(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("JWT token cannot be null");
        }
        
        Map<String, Object> userClaim = jwt.getClaim("user");
        if (userClaim == null) {
            throw new IllegalStateException("User claim is missing from the token.");
        }
        
        String userIdString = (String) userClaim.get("id");
        if (userIdString == null) {
            throw new IllegalStateException("User ID is missing from the user claim.");
        }
        
        return UUID.fromString(userIdString);
    }
}