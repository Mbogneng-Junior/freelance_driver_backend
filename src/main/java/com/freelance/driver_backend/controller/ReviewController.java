package com.freelance.driver_backend.controller;

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
