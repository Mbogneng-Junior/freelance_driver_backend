/*package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.RateCriteriaRequest;
import com.freelance.driver_backend.dto.CreateReviewRequest;
import com.freelance.driver_backend.model.Review;
import com.freelance.driver_backend.repository.ReviewRepository;
import com.freelance.driver_backend.service.ProfileService;
import com.freelance.driver_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Slf4j
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ProfileService profileService;

    @PostMapping("/criteria")
    public Mono<ResponseEntity<Void>> rateByCriteria(@RequestBody RateCriteriaRequest request) {
        // Logique pour traiter la notation par critères
        log.info("Received criteria rating for entity {}: {}", request.getEntityId(), request.getRatings());
        // Ici, vous ajouteriez la logique pour sauvegarder ces évaluations.
        // Pour l'instant, nous retournons simplement un succès.
        return Mono.just(ResponseEntity.ok().build());
    }

    @PostMapping
    public Mono<ResponseEntity<Review>> createReview(
            @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono) {

        return jwtMono
            .flatMap(jwt -> {
                UUID authorId = JwtUtil.getUserIdFromToken(jwt);
                return profileService.findProfileByUserId(authorId)
                    .flatMap(authorProfile -> {
                        Review review = new Review();
                        review.setId(UUID.randomUUID());
                        review.setTargetUserId(request.getTargetUserId());
                        review.setScore(request.getScore());
                        review.setComment(request.getComment());
                        review.setAuthorId(authorId);
                        review.setAuthorFirstName(profileService.getAuthorFirstNameFromProfile(authorProfile));
                        review.setAuthorLastName(profileService.getAuthorLastNameFromProfile(authorProfile));
                        review.setAuthorProfileImageUrl(profileService.getAvatarUrlFromProfile(authorProfile));
                        review.setCreatedAt(Instant.now().toEpochMilli());
                        
                        return reviewRepository.save(review);
                    });
            })
            .map(ResponseEntity::ok);
    }

    @GetMapping("/user/{userId}")
    public Flux<Review> getReviewsForUser(@PathVariable UUID userId) {
        log.info("Récupération des avis pour l'utilisateur ID: {}", userId);
        return reviewRepository.findByTargetUserId(userId)
                .flatMap(this::enrichReviewWithAuthorDetails);
    }

    private Mono<Review> enrichReviewWithAuthorDetails(Review review) {
        return profileService.findProfileByUserId(review.getAuthorId())
            .flatMap(authorProfile -> {
                review.setAuthorFirstName(profileService.getAuthorFirstNameFromProfile(authorProfile));
                review.setAuthorLastName(profileService.getAuthorLastNameFromProfile(authorProfile));
                review.setAuthorProfileImageUrl(profileService.getAvatarUrlFromProfile(authorProfile));
                return Mono.just(review);
            })
            .defaultIfEmpty(review); // En cas d'erreur (ex: auteur supprimé), on renvoie l'avis sans les détails
    }
}
*/

// PATH: /home/mbogneng-junior/freelance-driver (Copie)/backend/src/main/java/com/freelance/driver_backend/controller/ReviewController.java

package com.freelance.driver_backend.controller;

import com.freelance.driver_backend.dto.RateCriteriaRequest;
import com.freelance.driver_backend.dto.CreateReviewRequest;
import com.freelance.driver_backend.dto.UserSessionContextDto; // Importez la nouvelle structure
import com.freelance.driver_backend.model.ClientProfile; // Importez ClientProfile
import com.freelance.driver_backend.model.DriverProfile; // Importez DriverProfile
import com.freelance.driver_backend.model.Review;
import com.freelance.driver_backend.repository.ReviewRepository;
import com.freelance.driver_backend.service.ProfileService;
import com.freelance.driver_backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Slf4j
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ProfileService profileService;

    @PostMapping("/criteria")
    public Mono<ResponseEntity<Void>> rateByCriteria(@RequestBody RateCriteriaRequest request) {
        // Logique pour traiter la notation par critères
        log.info("Received criteria rating for entity {}: {}", request.getEntityId(), request.getRatings());
        // Ici, vous ajouteriez la logique pour sauvegarder ces évaluations.
        // Pour l'instant, nous retournons simplement un succès.
        return Mono.just(ResponseEntity.ok().build());
    }

    @PostMapping
    public Mono<ResponseEntity<Review>> createReview(
            @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal Mono<Jwt> jwtMono,
            @RequestHeader("Authorization") String authorizationHeader) { // Ajout pour getUserSessionContext

        return jwtMono
            .flatMap(jwt -> profileService.getUserSessionContext(JwtUtil.getUserIdFromToken(jwt), authorizationHeader, null))
            .flatMap(userContext -> {
                // L'auteur d'un avis peut être un client ou un chauffeur
                // On vérifie qu'il a au moins un profil
                if (userContext.getDriverProfile() == null && userContext.getClientProfile() == null) {
                    return Mono.error(new IllegalStateException("L'utilisateur n'a pas de profil actif pour laisser un avis."));
                }
                
                Review review = new Review();
                review.setId(UUID.randomUUID());
                review.setTargetUserId(request.getTargetUserId());
                review.setScore(request.getScore());
                review.setComment(request.getComment());
                review.setAuthorId(userContext.getUserId());
                
                // Détermine les infos de l'auteur en fonction du premier profil trouvé
                if (userContext.getDriverProfile() != null) {
                    review.setAuthorFirstName(userContext.getDriverProfile().getFirstName());
                    review.setAuthorLastName(userContext.getDriverProfile().getLastName());
                    review.setAuthorProfileImageUrl(userContext.getDriverProfile().getProfileImageUrl());
                } else if (userContext.getClientProfile() != null) {
                    review.setAuthorFirstName(userContext.getClientProfile().getFirstName());
                    review.setAuthorLastName(userContext.getClientProfile().getLastName());
                    review.setAuthorProfileImageUrl(userContext.getClientProfile().getProfileImageUrl());
                } else {
                    // Fallback si vraiment aucun profil n'est trouvé (ne devrait pas arriver avec la vérification ci-dessus)
                    review.setAuthorFirstName("Utilisateur");
                    review.setAuthorLastName("Anonyme");
                }
                
                review.setCreatedAt(Instant.now().toEpochMilli());
                
                return reviewRepository.save(review);
            })
            .map(ResponseEntity::ok);
    }

    @GetMapping("/user/{userId}")
    public Flux<Review> getReviewsForUser(@PathVariable UUID userId,
                                          @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        log.info("Récupération des avis pour l'utilisateur ID: {}", userId);
        return reviewRepository.findByTargetUserId(userId)
                .flatMap(review -> enrichReviewWithAuthorDetails(review, authorizationHeader)); // Passer l'header
    }

    // MODIFIÉ : Prend l'Authorization header pour pouvoir appeler getUserSessionContext
    private Mono<Review> enrichReviewWithAuthorDetails(Review review, String authorizationHeader) {
        return profileService.getUserSessionContext(review.getAuthorId(), authorizationHeader, null) // Utilise getUserSessionContext
            .flatMap(authorContext -> {
                if (authorContext.getDriverProfile() != null) {
                    review.setAuthorFirstName(authorContext.getDriverProfile().getFirstName());
                    review.setAuthorLastName(authorContext.getDriverProfile().getLastName());
                    review.setAuthorProfileImageUrl(authorContext.getDriverProfile().getProfileImageUrl());
                } else if (authorContext.getClientProfile() != null) {
                    review.setAuthorFirstName(authorContext.getClientProfile().getFirstName());
                    review.setAuthorLastName(authorContext.getClientProfile().getLastName());
                    review.setAuthorProfileImageUrl(authorContext.getClientProfile().getProfileImageUrl());
                }
                return Mono.just(review);
            })
            .defaultIfEmpty(review); // En cas d'erreur ou d'absence de profil, on renvoie l'avis sans les détails enrichis
    }
}