package com.freelance.driver_backend.service;

import com.freelance.driver_backend.dto.external.UploadMediaResponse;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Interface définissant les opérations pour un service de stockage de fichiers.
 */
public interface StorageService {

    /**
     * Sauvegarde un fichier et renvoie la réponse complète du service de médias, y compris son URL publique et son URI interne.
     * Le service gérera l'obtention de son propre token d'authentification M2M.
     *
     * @param serviceContext     Le contexte de service pour l'API externe (ex: "product", "resource").
     * @param frontendLogicalType Le type logique du frontend (ex: "avatars", "vehicles", "documents").
     * @param uploaderUserId     L'ID de l'utilisateur qui téléverse le fichier (utilisé pour la structuration du chemin).
     * @param targetResourceId   L'ID de la ressource à laquelle ce fichier est associé (ex: l'ID de l'utilisateur, l'ID du véhicule).
     * @param originalFileName   Le nom de fichier original.
     * @param file               Le fichier à sauvegarder.
     * @return Un Mono contenant l'objet UploadMediaResponse avec l'URL publique et l'URI interne du fichier sauvegardé.
     */
    Mono<UploadMediaResponse> saveFile(String serviceContext, String frontendLogicalType, UUID uploaderUserId, UUID targetResourceId, String originalFileName, FilePart file);

    /**
     * Supprime un fichier en utilisant son URI interne retourné par le service de médias.
     * Le service gérera l'obtention de son propre token d'authentification M2M.
     *
     * @param mediaUri           L'URI interne du fichier (ex: "/product/image/avatars/user-id/filename.jpg").
     * @return Un Mono<Void> qui se termine lorsque la suppression est terminée.
     */
    Mono<Void> deleteFile(String mediaUri);
}