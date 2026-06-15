package com.bupt.charging.service;

import com.bupt.charging.domain.AccountRole;
import com.bupt.charging.domain.LoginSession;
import com.bupt.charging.domain.UserAccount;
import com.bupt.charging.domain.Vehicle;
import com.bupt.charging.dto.AuthDtos;
import com.bupt.charging.repository.LoginSessionRepository;
import com.bupt.charging.repository.UserAccountRepository;
import com.bupt.charging.repository.VehicleRepository;
import com.bupt.charging.support.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String DEFAULT_ADMIN = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "123456";

    private final UserAccountRepository userAccountRepository;
    private final VehicleRepository vehicleRepository;
    private final LoginSessionRepository loginSessionRepository;

    public AuthService(
            UserAccountRepository userAccountRepository,
            VehicleRepository vehicleRepository,
            LoginSessionRepository loginSessionRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.vehicleRepository = vehicleRepository;
        this.loginSessionRepository = loginSessionRepository;
    }

    @Transactional
    public AuthDtos.LoginResponse login(String loginName, String password) {
        ensureDefaultAdmin();
        Vehicle vehicle = vehicleRepository.findByCarId(loginName).orElse(null);
        if (vehicle != null) {
            UserAccount owner = vehicle.getOwner();
            requirePassword(owner, password);
            return createSession(owner, vehicle.getCarId());
        }

        UserAccount admin = userAccountRepository.findFirstByUserNameAndRole(loginName, AccountRole.ADMIN)
                .orElseThrow(() -> new BusinessException("invalid login credentials"));
        requirePassword(admin, password);
        return createSession(admin, null);
    }

    @Transactional(readOnly = true)
    public AuthDtos.CurrentUserResponse currentUser(String token) {
        LoginSession session = loginSessionRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException("session not found"));
        if (session.isExpired(LocalDateTime.now())) {
            throw new BusinessException("session expired");
        }
        UserAccount account = userAccountRepository.findById(session.getAccountId())
                .orElseThrow(() -> new BusinessException("account not found"));
        return new AuthDtos.CurrentUserResponse(
                session.getRole(),
                account.getId(),
                account.getUserName(),
                session.getCarId()
        );
    }

    @Transactional
    public void logout(String token) {
        loginSessionRepository.deleteByToken(token);
    }

    private AuthDtos.LoginResponse createSession(UserAccount account, String carId) {
        LocalDateTime now = LocalDateTime.now();
        LoginSession session = loginSessionRepository.save(new LoginSession(
                UUID.randomUUID().toString(),
                account.getId(),
                account.getRole(),
                carId,
                now,
                now.plusHours(8)
        ));
        return new AuthDtos.LoginResponse(
                session.getToken(),
                account.getRole(),
                account.getId(),
                account.getUserName(),
                carId
        );
    }

    private void ensureDefaultAdmin() {
        userAccountRepository.findFirstByUserNameAndRole(DEFAULT_ADMIN, AccountRole.ADMIN)
                .orElseGet(() -> {
                    UserAccount admin = new UserAccount(DEFAULT_ADMIN, AccountRole.ADMIN, LocalDateTime.now());
                    admin.setPasswordHash(hashPassword(DEFAULT_ADMIN_PASSWORD));
                    return userAccountRepository.save(admin);
                });
    }

    private void requirePassword(UserAccount account, String password) {
        if (!"ACTIVE".equals(account.getStatus()) || !hashPassword(password).equals(account.getPasswordHash())) {
            throw new BusinessException("invalid login credentials");
        }
    }

    static String hashPassword(String password) {
        return Base64.getEncoder().encodeToString(("demo:" + password).getBytes(StandardCharsets.UTF_8));
    }
}
