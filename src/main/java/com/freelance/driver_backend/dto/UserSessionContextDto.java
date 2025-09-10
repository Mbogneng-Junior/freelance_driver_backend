package com.freelance.driver_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.freelance.driver_backend.dto.external.OrganisationDto;
import com.freelance.driver_backend.model.ClientProfile; // Nouveau import
import com.freelance.driver_backend.model.DriverProfile; // Nouveau import
import lombok.Builder;
import lombok.Data;
import java.util.List; // Import modifié pour une liste de rôles
import java.util.UUID;

@Data
@Builder // Le Builder sera très pratique pour construire cet objet complexe
@JsonInclude(JsonInclude.Include.NON_NULL) // N'inclut que les champs non nuls dans le JSON
public class UserSessionContextDto {

    private UUID userId;
    // MODIFIÉ : Une liste de rôles, permettant plusieurs rôles
    private List<UserRole> roles;
    // MODIFIÉ : Des objets de profil spécifiques, peuvent être nuls
    private DriverProfile driverProfile;
    private ClientProfile clientProfile;
    // L'objet Organisation enrichi. Un utilisateur aura une organisation "principale" (par exemple, la première créée)
    private OrganisationDto organisation;

    public enum UserRole {
        DRIVER,
        CLIENT,
        NO_PROFILE // Représente un utilisateur avec un compte d'authentification mais sans profil spécifique encore
    }
}