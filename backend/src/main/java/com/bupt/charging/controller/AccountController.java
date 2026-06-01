package com.bupt.charging.controller;

import com.bupt.charging.dto.AccountDtos;
import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ApiResult<AccountDtos.AccountResponse> create(@Valid @RequestBody AccountDtos.CreateAccountRequest request) {
        return ApiResult.ok(accountService.createNewAccount(request.carId(), request.userName(), request.carCapacity()));
    }

    @PostMapping("/{carId}/password")
    public ApiResult<AccountDtos.AccountResponse> setPassword(
            @PathVariable String carId,
            @Valid @RequestBody AccountDtos.SetPasswordRequest request
    ) {
        return ApiResult.ok(accountService.setPassword(carId, request.password()));
    }
}
