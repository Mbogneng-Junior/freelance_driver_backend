package com.freelance.driver_backend.model;

import lombok.Data;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;
import java.util.UUID;
import org.springframework.data.cassandra.core.mapping.Indexed;

@Table("client_profiles")
@Data
public class ClientProfile {
    @PrimaryKey
    private UUID id;

    @Indexed
    @Column("user_id")
    private UUID userId;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("profile_image_url")
    private String profileImageUrl;

    // Informations de l'entreprise
    @Column("company_name")
    private String companyName;

    // Informations du contact principal
    @Column("first_name")
    private String firstName;

    @Column("last_name")
    private String lastName;

    @Column("nickname")
    private String nickname;

    @Column("contact_email")
    private String contactEmail;

    @Column("phone_number")
    private String phoneNumber;

    // Informations personnelles additionnelles
    @Column("birth_date")
    private String birthDate;

    @Column("nationality")
    private String nationality;

    @Column("gender")
    private String gender;

    @Column("language")
    private String language;
}