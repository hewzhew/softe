package com.bupt.charging.repository;

import com.bupt.charging.domain.Vehicle;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findByCarId(String carId);

    boolean existsByCarId(String carId);
}
