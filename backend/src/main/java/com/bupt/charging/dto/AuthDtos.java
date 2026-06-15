package com.bupt.charging.dto;

import com.bupt.charging.domain.AccountRole;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String loginName, @NotBlank String password) {
    }

    public record LoginResponse(
            String token,
            AccountRole role,
            Long accountId,
            String userName,
            String carId
    ) {
    }

    public record CurrentUserResponse(
            AccountRole role,
            Long accountId,
            String userName,
            String carId
    ) {
    }
}
