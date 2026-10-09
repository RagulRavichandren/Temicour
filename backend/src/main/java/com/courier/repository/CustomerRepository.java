package com.courier.repository;

import com.courier.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
}
