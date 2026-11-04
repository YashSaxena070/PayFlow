package com.payflow.provider.repository;

import com.payflow.provider.domain.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, UUID> {
    Optional<Provider> findByCode(String code);
    Optional<Provider> findByCodeIgnoreCase(String code);
}
