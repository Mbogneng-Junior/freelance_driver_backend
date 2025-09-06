// src/main/java/com/freelance/driver_backend/dto/CreateProductRequest.java

package com.freelance.driver_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * DTO (Data Transfer Object) universel pour la création et la mise à jour de "Produits".
 * Il regroupe tous les champs potentiels envoyés par le frontend pour différentes entités
 * (Annonces, Plannings, Véhicules, Adresses, etc.).
 */
@Data
public class CreateProductRequest {
    
    // --- Champs Communs ---
    private String name;
    private String shortDescription;
    private UUID categoryId;
    private Boolean isActive;
    private String status;
    private BigDecimal defaultSellPrice;

    // --- Champs de Trajet (Annonces/Plannings) ---
    private String pickupLocation;
    private String dropoffLocation;
    private String startDate;
    private String startTime;
    private String endDate;
    private String endTime;
    
    // --- Champs d'Annonce Spécifiques ---
    private String baggageInfo;
    private boolean isNegotiable;
    private String paymentMethod;

    // --- Champs d'Auteur (Client ou Conducteur) ---
    private UUID clientId;
    private String clientName;
    private String clientPhoneNumber;
    private String clientProfileImageUrl;

    // --- Champs Spécifiques au Planning (stockés dans metadata par le controller) ---
    private String paymentOption;
    private BigDecimal regularAmount;
    private String discountPercentage;
    private BigDecimal discountedAmount;
    
    // --- Stockage Flexible ---
    private Map<String, String> metadata;
}