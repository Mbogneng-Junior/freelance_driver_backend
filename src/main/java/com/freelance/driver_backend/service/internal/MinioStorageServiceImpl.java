package com.freelance.driver_backend.service.internal;

import com.freelance.driver_backend.service.StorageService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.util.UUID;
import io.minio.RemoveObjectArgs;

@Service
@Slf4j
public class MinioStorageServiceImpl implements StorageService {

    private final MinioClient minioClient;
    private final String bucketName;
    private final String endpoint;

    public MinioStorageServiceImpl(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${minio.bucket-name}") String bucketName
    ) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucketName = bucketName;
        this.endpoint = endpoint;
    }

    @Override
    public Mono<String> saveFile(String basePath, String oldFileName, FilePart file) {
        return DataBufferUtils.join(file.content())
            .flatMap(dataBuffer -> {
                String fileExtension = getFileExtension(file.filename());
                String objectName = String.format("%s/%s%s", basePath, UUID.randomUUID(), fileExtension);
                
                try (InputStream inputStream = dataBuffer.asInputStream(true)) {
                    minioClient.putObject(
                        PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, dataBuffer.readableByteCount(), -1)
                            .contentType(file.headers().getContentType().toString())
                            .build()
                    );
                    String fileUrl = String.format("%s/%s/%s", this.endpoint, this.bucketName, objectName);
                    log.info("Fichier téléversé avec succès. URL: {}", fileUrl);
                    return Mono.just(fileUrl);
                } catch (Exception e) {
                    log.error("Erreur lors du téléversement vers MinIO", e);
                    return Mono.error(new RuntimeException("Erreur de téléversement de fichier", e));
                }
            }).subscribeOn(Schedulers.boundedElastic()); // Déléguer l'opération bloquante à un thread dédié
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
    
    @Override
    public Mono<Void> deleteFile(String objectName) {
        return Mono.fromRunnable(() -> {
            try {
                log.info("Suppression du fichier '{}' du bucket MinIO '{}'", objectName, bucketName);
                minioClient.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
                );
                log.info("Suppression réussie.");
            } catch (Exception e) {
                log.error("Erreur lors de la suppression du fichier sur MinIO", e);
                // On ne propage pas l'erreur pour ne pas faire échouer tout le processus d'upload
                // si seule la suppression de l'ancien fichier échoue.
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}