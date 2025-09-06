package com.freelance.driver_backend.repository;

import com.freelance.driver_backend.model.Product;
import com.freelance.driver_backend.model.ProductKey;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import reactor.core.publisher.Flux;
import java.util.UUID;

public interface ProductRepository extends ReactiveCassandraRepository<Product, ProductKey> {

    Flux<Product> findByKeyOrganizationIdAndCategoryId(UUID organizationId, UUID categoryId);

    @Query("SELECT * FROM products WHERE category_id = ?0 ALLOW FILTERING")
    Flux<Product> findByCategoryId(UUID categoryId);
    
    Flux<Product> findByKeyOrganizationId(UUID organizationId);

    @Query("SELECT * FROM products WHERE client_id = ?0 AND category_id = ?1 ALLOW FILTERING")
    Flux<Product> findByClientIdAndCategoryId(UUID clientId, UUID categoryId);
    
    @Query("SELECT * FROM products WHERE reserved_by_driver_id = ?0 ALLOW FILTERING")
    Flux<Product> findByReservedByDriverId(UUID driverId);
}