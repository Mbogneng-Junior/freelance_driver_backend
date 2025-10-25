package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.external.*;
import com.freelance.driver_backend.model.mock.MockUser;
import com.freelance.driver_backend.repository.mock.MockUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MockUserService {
    
   private final MockUserRepository mockUserRepository;
    private final JwtService jwtService;

    public Mono<UserDto> register(RegistrationRequest request) {
        return mockUserRepository.findByEmail(request.getEmail())
                .hasElement()
                .flatMap(emailExists -> {
                    if (emailExists) {
                        return Mono.error(new RuntimeException("Email déjà utilisé"));
                    }
                    return mockUserRepository.findByUsername(request.getUsername()).hasElement();
                })
                .flatMap(usernameExists -> {
                    if (usernameExists) {
                        return Mono.error(new RuntimeException("Username déjà utilisé"));
                    }
                    
                    MockUser user = new MockUser();
                    user.setId(UUID.randomUUID());
                    user.setUsername(request.getUsername());
                    user.setPassword(request.getPassword());
                    user.setEmail(request.getEmail());
                    user.setFirstName(request.getFirstName());
                    user.setLastName(request.getLastName());
                    user.setPhoneNumber(request.getPhoneNumber());
                    // Supprimer cette ligne si RegistrationRequest n'a pas organisationId
                    // user.setOrganisationId(request.getOrganisationId()); 
                    //user.setOrganisationId(null); // Ou définir une valeur par défaut

                    return mockUserRepository.save(user);
                })
                .map(this::convertToUserDto);
    }

    public Mono<LoginResponse> login(LoginRequest request) {
        return mockUserRepository.findByUsername(request.getUsername())
                .switchIfEmpty(Mono.error(new RuntimeException("Identifiants invalides")))
                .filter(user -> user.getPassword().equals(request.getPassword()))
                .switchIfEmpty(Mono.error(new RuntimeException("Identifiants invalides")))
                .map(user -> {
                    // JwtService est maintenant synchrone
                    String token = jwtService.generateToken(user);
                    return createLoginResponse(user, token);
                });
    }

    private LoginResponse createLoginResponse(MockUser user, String jwtToken) {
        LoginResponse response = new LoginResponse();
        
        // Access Token avec vrai JWT
        LoginResponse.AccessToken accessToken = new LoginResponse.AccessToken();
        accessToken.setToken(jwtToken);
        accessToken.setType("Bearer");
        accessToken.setExpiresIn(3600);
        response.setAccessToken(accessToken);
        
        // User Info
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setFirstName(user.getFirstName());
        userInfo.setLastName(user.getLastName());
        userInfo.setUsername(user.getUsername());
        userInfo.setEmail(user.getEmail());
        userInfo.setPhoneNumber(user.getPhoneNumber());
        userInfo.setEmailVerified(false);
        userInfo.setPhoneNumberVerified(false);
        response.setUser(userInfo);
        
        response.setRoles(List.of("USER"));
        response.setPermissions(List.of("READ_PROFILE", "UPDATE_PROFILE"));
        
        return response;
    }

    private UserDto convertToUserDto(MockUser user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setEmailVerified(false);
        dto.setPhoneNumberVerified(false);
        dto.setEnabled(true);
        dto.setCreatedAt(OffsetDateTime.now());
        dto.setUpdatedAt(OffsetDateTime.now());
        return dto;
    }
}