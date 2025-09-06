package com.freelance.driver_backend.repository;

import com.freelance.driver_backend.model.Review;
import org.springframework.data.cassandra.repository.AllowFiltering;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import reactor.core.publisher.Flux;
import java.util.UUID;

public interface ReviewRepository extends ReactiveCassandraRepository<Review, UUID> {
    @AllowFiltering
    Flux<Review> findByTargetUserId(UUID targetUserId);
}
