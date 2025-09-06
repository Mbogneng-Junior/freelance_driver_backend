package com.freelance.driver_backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;
import java.util.UUID;

@Table("reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @PrimaryKey
    private UUID id;

    private UUID targetUserId; // L'utilisateur qui est noté
    private UUID authorId;     // L'utilisateur qui a écrit la note
    private String authorFirstName;
    private String authorLastName;
    private String authorProfileImageUrl;
    private int score;
    private String comment;
    private long createdAt;
}
