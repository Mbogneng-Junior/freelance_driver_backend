// src/main/java/com/freelance/driver_backend/service/StorageService.java

package com.freelance.driver_backend.service;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

/**
 * Interface définissant les opérations pour un service de stockage de fichiers.
 */
public interface StorageService {
    
    /**
     * Sauvegarde un fichier et renvoie son URL publique.
     * @param basePath Le chemin de base où stocker le fichier (ex: "avatars/user-id").
     * @param originalFileName Le nom de fichier original.
     * @param file Le fichier à sauvegarder.
     * @return Un Mono contenant l'URL publique du fichier sauvegardé.
     */
    Mono<String> saveFile(String basePath, String originalFileName, FilePart file);
    
    /**
     * Supprime un fichier à partir de son nom d'objet complet.
     * @param objectName Le chemin complet du fichier dans le bucket (ex: "avatars/user-id/resource-id/filename.jpg").
     * @return Un Mono<Void> qui se termine lorsque la suppression est terminée.
     */
    Mono<Void> deleteFile(String objectName);
}