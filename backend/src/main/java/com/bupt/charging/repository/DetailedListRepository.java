package com.bupt.charging.repository;

import com.bupt.charging.domain.DetailedList;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetailedListRepository extends JpaRepository<DetailedList, Long> {
    List<DetailedList> findByBillId(Long billId);
}
