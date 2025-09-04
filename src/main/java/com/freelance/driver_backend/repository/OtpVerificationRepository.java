package com.freelance.driver_backend.repository;

import com.freelance.driver_backend.model.OtpVerification;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OtpVerificationRepository extends ReactiveCassandraRepository<OtpVerification, String> {
    // Spring Data se charge de tout, cette interface peut rester vide.
}