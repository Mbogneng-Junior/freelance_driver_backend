package com.freelance.driver_backend.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.UUID;

/**
 * Représente la réponse JSON de la nouvelle API de médias externe.
 * Ignore les champs non nécessaires pour notre logique.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaApiResponseDto {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("uri")
    private String uri; // L'URI relative complète du fichier

    // Helper pour reconstruire l'URL publique que notre application attend
    public String getPublicUrl(String baseUrl) {
        if (baseUrl == null || id == null) {
            return null;
        }
        // La nouvelle API utilise /media/proxy/{id} pour servir les fichiers
        return baseUrl + "/media/proxy/" + this.id.toString();
    }
}