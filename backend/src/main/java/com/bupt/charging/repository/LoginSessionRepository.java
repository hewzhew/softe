package com.bupt.charging.repository;

import com.bupt.charging.domain.LoginSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
    Optional<LoginSession> findByToken(String token);

    void deleteByToken(String token);
}
