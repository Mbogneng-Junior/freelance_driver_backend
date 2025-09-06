package com.freelance.driver_backend.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateReviewRequest {
    private UUID targetUserId;
    private int score;
    private String comment;
}
