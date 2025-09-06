package com.freelance.driver_backend.dto;

import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
public class RateCriteriaRequest {
    private String entityId;
    private Map<String, Integer> ratings;
}
