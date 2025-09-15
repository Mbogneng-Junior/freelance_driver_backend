package com.freelance.driver_backend.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class NotificationRequest {

    @JsonProperty("template_id")
    private UUID templateId;

    private String subject;
    private String body;
    private List<String> recipients;
    private String type; // ex: "EMAIL", "SMS"
    private String priority; // ex: "LEVEL_1"
    

    // Pour les variables Thymeleaf
    private Map<String, String> metadata;
    private Map<String, String> data;
}