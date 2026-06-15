package com.bupt.charging.repository;

import com.bupt.charging.domain.AccountRole;
import com.bupt.charging.domain.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findFirstByUserNameAndRole(String userName, AccountRole role);
}
