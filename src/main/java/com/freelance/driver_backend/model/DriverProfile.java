package com.freelance.driver_backend.model;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;
import org.springframework.data.cassandra.core.mapping.Indexed;
import java.util.UUID;
import lombok.Data;

@Table("driver_profiles")
@Data
public class DriverProfile {

    @PrimaryKey
    private UUID id;

    @Indexed
    @Column("user_id")
    private UUID userId;

    @Column("organisation_id")
    private UUID organisationId;

    @Column("first_name")
    private String firstName;

    @Column("last_name")
    private String lastName;

    @Column("phone_number")
    private String phoneNumber;

    @Column("license_number")
    private String licenseNumber;

    @Column("vehicle_details")
    private String vehicleDetails;

    @Column("profile_image_url")
    private String profileImageUrl;

    @Column("nickname")
    private String nickname;
    @Column("birth_date")
    private String birthDate; // On garde en String pour la simplicité
    @Column("nationality")
    private String nationality;
    @Column("gender")
    private String gender;
    @Column("language")
    private String language;
    @Column("biography")
    private String biography;
}
