package com.bupt.charging.repository;

import com.bupt.charging.domain.Bill;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRepository extends JpaRepository<Bill, Long> {
    List<Bill> findByCarIdAndBillDateOrderByGeneratedAtDesc(String carId, LocalDate billDate);

    List<Bill> findTop10ByOrderByGeneratedAtDesc();
}
