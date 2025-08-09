package com.freelance.driver_backend.dto.external;

import lombok.Data;
import java.util.UUID;

/**
 * Représente la réponse JSON obtenue après un téléversement réussi
 * auprès du service de média.
 */
@Data
public class UploadMediaResponse {
    private UUID id;
    private UUID resourceId;
    private String uri;
    private String url;
}