// src/main/java/com/freelance/driver_backend/model/Product.java

package com.freelance.driver_backend.model;

import lombok.Data;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.Indexed;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.annotation.Transient;

@Table("products")
@Data
public class Product {
    @PrimaryKey
    private ProductKey key;

    // --- Champs Communs ---
    private String name; // Utilisé pour le titre de l'annonce/planning, le modèle du véhicule, le titre de l'adresse...
    
    @Column("short_description")
    private String shortDescription; // Utilisé pour les bagages, la description du véhicule, le code postal...
    
    @Indexed
    @Column("category_id")
    private UUID categoryId; // L'identifiant qui distingue un véhicule d'une annonce, etc.
    
    @Column("is_active")
    private Boolean isActive;

    @Column("status")
    private String status; // Draft, Published, Confirmed, Terminated, METADATA, etc.
    
    @Column("default_sell_price")
    private BigDecimal defaultSellPrice; // Coût de la course, etc.

    // --- Champs Spécifiques au Trajet (Annonces & Plannings) ---
    @Column("pickup_location")
    private String pickupLocation;
    
    @Column("dropoff_location")
    private String dropoffLocation;
    
    @Column("start_date")
    private String startDate;
    
    @Column("start_time")
    private String startTime;

    @Column("end_date")
    private String endDate;

    @Column("end_time")
    private String endTime;

    @Column("baggage_info")
    private String baggageInfo;
    
    @Column("is_negotiable")
    private Boolean isNegotiable;
    
    @Column("payment_method")
    private String paymentMethod;
    
    // --- Info sur l'Auteur (client ou conducteur) ---
    @Indexed
    @Column("client_id")
    private UUID clientId; // L'ID de l'utilisateur qui a créé ce "produit"
    
    @Column("client_name")
    private String clientName;

    @Column("client_phone_number")
    private String clientPhoneNumber;

    @Column("client_profile_image_url")
    private String clientProfileImageUrl; // Utilisé pour l'avatar de l'auteur, ou la photo principale du véhicule

    // --- Info sur la Réservation ---
    @Indexed
    @Column("reserved_by_driver_id")
    private UUID reservedByDriverId; // ID du chauffeur (pour une annonce) ou du client (pour un planning)

    @Column("reserved_by_driver_name")
    private String reservedByDriverName;

    // --- Stockage flexible pour les champs non-standards ---
    @Column("metadata") 
    private Map<String, String> metadata; // Pour les détails du véhicule, les options de paiement du planning, etc.

    // --- Champs Transitoires pour l'enrichissement ---
    @Transient
    private UUID authorId;
    @Transient
    private String authorName;
    @Transient
    private String authorPhoneNumber;
    @Transient
    private String authorProfileImageUrl;


    // --- Méthodes d'accès pratiques ---
    public UUID getId() { return this.key != null ? this.key.getId() : null; }
    public UUID getOrganizationId() { return this.key != null ? this.key.getOrganizationId() : null; }
}