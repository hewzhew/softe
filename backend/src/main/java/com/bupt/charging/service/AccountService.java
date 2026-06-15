package com.bupt.charging.service;

import com.bupt.charging.domain.AccountRole;
import com.bupt.charging.domain.UserAccount;
import com.bupt.charging.domain.Vehicle;
import com.bupt.charging.dto.AccountDtos;
import com.bupt.charging.repository.UserAccountRepository;
import com.bupt.charging.repository.VehicleRepository;
import com.bupt.charging.support.BusinessException;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserAccountRepository userAccountRepository;
    private final VehicleRepository vehicleRepository;

    public AccountService(UserAccountRepository userAccountRepository, VehicleRepository vehicleRepository) {
        this.userAccountRepository = userAccountRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public AccountDtos.AccountResponse findAccount(String carId) {
        Vehicle vehicle = vehicleRepository.findByCarId(carId)
                .orElseThrow(() -> new BusinessException("vehicle not found"));
        UserAccount account = vehicle.getOwner();
        return new AccountDtos.AccountResponse(
                vehicle.getCarId(),
                account.getUserName(),
                vehicle.getCarCapacity(),
                account.getStatus(),
                account.getRole()
        );
    }

    @Transactional
    public AccountDtos.AccountResponse createNewAccount(String carId, String userName, double carCapacity) {
        if (vehicleRepository.existsByCarId(carId)) {
            throw new BusinessException("carId already registered");
        }
        if (carCapacity <= 0) {
            throw new BusinessException("car capacity must be positive");
        }
        UserAccount account = userAccountRepository.save(new UserAccount(userName, AccountRole.OWNER, LocalDateTime.now()));
        Vehicle vehicle = vehicleRepository.save(new Vehicle(carId, carCapacity, account));
        return new AccountDtos.AccountResponse(
                vehicle.getCarId(),
                account.getUserName(),
                vehicle.getCarCapacity(),
                account.getStatus(),
                account.getRole()
        );
    }

    @Transactional
    public AccountDtos.AccountResponse setPassword(String carId, String password) {
        Vehicle vehicle = vehicleRepository.findByCarId(carId)
                .orElseThrow(() -> new BusinessException("vehicle not found"));
        UserAccount account = vehicle.getOwner();
        String hash = AuthService.hashPassword(password);
        account.setPasswordHash(hash);
        userAccountRepository.save(account);
        return new AccountDtos.AccountResponse(
                vehicle.getCarId(),
                account.getUserName(),
                vehicle.getCarCapacity(),
                account.getStatus(),
                account.getRole()
        );
    }
}
