package com.freelance.driver_backend.model;

public enum AnnouncementStatus {
    DRAFT, // Annonce créée mais pas encore visible
    PUBLISHED, // Annonce visible pour les chauffeurs
    PENDING_CONFIRMATION, // Un chauffeur a postulé, en attente de la confirmation du client
    CONFIRMED, // Le client a confirmé le chauffeur
    TERMINATED, // La course est terminée
    CANCELLED // La course est annulée
}
