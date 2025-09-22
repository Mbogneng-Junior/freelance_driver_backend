package com.freelance.driver_backend.model;

public enum AnnouncementStatus {
    DRAFT, // Annonce créée mais pas encore visible
    PUBLISHED, // Annonce visible pour les chauffeurs ou Planning visible pour les clients
    PENDING_CONFIRMATION, // Un chauffeur a postulé, en attente de la confirmation du client (pour ANNONCE)
    PENDING_DRIVER_CONFIRMATION, // Un client a demandé une réservation, en attente de la confirmation du chauffeur (pour PLANNING)
    CONFIRMED, // Le client a confirmé le chauffeur (pour ANNONCE) ou le planning est confirmé (pour PLANNING) - note: CONFIRMED est souvent une étape avant ONGOING
    ONGOING, // La course est en cours
    TERMINATED, // La course est terminée
    CANCELLED // La course est annulée
}