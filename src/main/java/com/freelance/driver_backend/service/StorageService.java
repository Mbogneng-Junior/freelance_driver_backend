package com.freelance.driver_backend.service;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface StorageService {
    Mono<String> saveFile(String basePath, String oldFileName, FilePart file);
    Mono<Void> deleteFile(String objectName);
}