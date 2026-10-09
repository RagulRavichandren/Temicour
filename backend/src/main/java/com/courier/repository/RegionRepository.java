package com.courier.repository;

import com.courier.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RegionRepository extends JpaRepository<Region, Long> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    java.util.Optional<Region> findByNameIgnoreCase(String name);
    java.util.Optional<Region> findByUsernameIgnoreCase(String username);
    List<Region> findByParentDistrictIgnoreCase(String district);
    List<Region> findByParentDistrictIgnoreCaseAndAccountTypeIgnoreCase(String district, String accountType);
}
