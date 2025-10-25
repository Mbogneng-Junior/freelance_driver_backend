package com.freelance.driver_backend.repository.mock;

import com.freelance.driver_backend.model.mock.MockUser;
import org.springframework.data.cassandra.repository.AllowFiltering;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface MockUserCassandraRepository extends ReactiveCassandraRepository<MockUser, UUID> {
    
    @AllowFiltering
    Mono<MockUser> findByEmail(String email);
    
    @AllowFiltering
    Mono<MockUser> findByUsername(String username);
}