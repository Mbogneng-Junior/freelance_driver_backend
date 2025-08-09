package com.freelance.driver_backend.repository;

import com.freelance.driver_backend.model.DeviceToken;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import java.util.UUID;

@Repository
public interface DeviceTokenRepository extends ReactiveCassandraRepository<DeviceToken, UUID> {
    Flux<DeviceToken> findByUserId(UUID userId);
}