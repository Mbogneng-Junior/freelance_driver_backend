package com.freelance.driver_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    // ==============================================================================
    //                       CHAÎNE DE FILTRES DE SÉCURITÉ UNIQUE
    // Une seule chaîne de filtres pour gérer toutes les routes de l'API.
    // ==============================================================================
    
    @Bean
    @Order(0) // Priorité la plus haute
    public SecurityWebFilterChain apiFilterChain(ServerHttpSecurity http) throws Exception {
        http
            // Appliquer cette configuration à toutes les routes sous /api/
            .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/**"))
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // --- Routes Publiques (permitAll) ---
                
                // POST publics (Login, Register, Onboarding)
                .pathMatchers(HttpMethod.POST, 
                    "/api/register", 
                    "/api/auth/login", 
                    "/api/onboarding/**"
                ).permitAll()
                
                // GET publics (Recherche, Profils publics, Annonces publiées)
                .pathMatchers(HttpMethod.GET,
                    "/api/announcements", 
                    "/api/planning/published", 
                    "/api/planning/user/**",
                    "/api/search/**",           // <--- AJOUTÉ ICI : Autorise la recherche de conducteurs
                    "/api/reviews/user/**", 
                    "/api/profiles/user/**", 
                    "/api/vehicles/user/**", 
                    "/api/addresses/user/**", 
                    "/api/experiences/user/**"
                ).permitAll()

                // Routes pour les mocks de développement
                .pathMatchers("/api/mock-**/**").permitAll()
                .pathMatchers("/api/mock_user/**", "/api/mock_auth/**").permitAll()

                // --- Routes Sécurisées (authenticated) ---
                // Toutes les autres requêtes nécessitent une authentification
                .anyExchange().authenticated()
            )
            // Configurer le serveur de ressources OAuth2 pour valider les tokens JWT
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> { }));

        return http.build();
    }

    // ==============================================================================
    //                       CONFIGURATION CORS ET JWT
    // ==============================================================================

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOriginPattern("*"); // Autorise toutes les origines (pour DEV)
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
  
   @Bean
    public ReactiveJwtDecoder jwtDecoder(KeyPair keyPair) {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        return NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
    }
}