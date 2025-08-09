package com.freelance.driver_backend.model;

import lombok.Data;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.Indexed;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Table("products")
@Data
public class Product {
    @PrimaryKey
    private ProductKey key;

    private String name;
    @Column("short_description")
    private String shortDescription;
    @Indexed
    @Column("category_id")
    private UUID categoryId;
    @Column("is_active")
    private Boolean isActive;

    @Column("status")
    private String status;


    @Column("default_sell_price")
    private BigDecimal defaultSellPrice;


    // Champs d'annonce spécifiques
    @Column("pickup_location")
    private String pickupLocation;
    @Column("dropoff_location")
    private String dropoffLocation;
    @Column("start_date")
    private String startDate;
    @Column("start_time")
    private String startTime;

    // ==============================================================================
    //                         AJOUTEZ CES DEUX LIGNES
    // ==============================================================================
    @Column("end_date")
    private String endDate;   // ex: "2025-08-05"

    @Column("end_time")
    private String endTime;   // ex: "18:00"
    // ==============================================================================

    @Column("baggage_info")
    private String baggageInfo;
    @Column("is_negotiable")
    private Boolean isNegotiable;
    @Column("payment_method")
    private String paymentMethod;
    @Column("client_id")
    private UUID clientId;
    @Column("client_name")
    private String clientName;

    @Column("client_phone_number")
    private String clientPhoneNumber;
    @Column("payment_option")
    private String paymentOption; // "per_km", "fixed", etc.

    @Column("regular_amount")
    private BigDecimal regularAmount;

    @Column("discount_percentage")
    private String discountPercentage; // Gardé en String pour la simplicité (ex: "10")

    @Column("discounted_amount")
    private BigDecimal discountedAmount;

    public UUID getId() { return this.key != null ? this.key.getId() : null; }
    public UUID getOrganizationId() { return this.key != null ? this.key.getOrganizationId() : null; }
}