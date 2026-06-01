package com.bupt.charging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public final class AccountDtos {
    private AccountDtos() {
    }

    public record CreateAccountRequest(
            @NotBlank String carId,
            @NotBlank String userName,
            @Positive double carCapacity
    ) {
    }

    public record SetPasswordRequest(@NotBlank String password) {
    }

    public record AccountResponse(String carId, String userName, double carCapacity, String status) {
    }
}
