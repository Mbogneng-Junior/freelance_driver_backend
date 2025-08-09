package com.freelance.driver_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateProductRequest {
    @JsonProperty("title")
    private String name;
    @JsonProperty("cost")
    private BigDecimal defaultSellPrice;
    @JsonProperty("baggageInfo")
    private String shortDescription;

    private UUID categoryId;
    private Boolean isActive;
    private String pickupLocation;
    private String dropoffLocation;
    private String startDate;
    private String startTime;
    private String status;
    private String clientPhoneNumber;

    // ==============================================================================
    //                         AJOUTEZ CES DEUX LIGNES
    // ==============================================================================
    private String endDate;
    private String endTime;

    private String paymentOption;
    private BigDecimal regularAmount;
    private String discountPercentage;
    private BigDecimal discountedAmount;
    // ==============================================================================

    private boolean isNegotiable;
    private String paymentMethod;
    private UUID clientId;
    private String clientName;
}