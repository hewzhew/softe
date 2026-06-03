package com.bupt.charging.controller;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.BillingDtos;
import com.bupt.charging.dto.ConfigDtos;
import com.bupt.charging.service.AccountService;
import com.bupt.charging.service.ChargingService;
import com.bupt.charging.service.ConfigService;
import com.bupt.charging.service.PileService;
import com.bupt.charging.service.QueueService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConfigController {
    private final ConfigService configService;
    private final AccountService accountService;
    private final ChargingService chargingService;
    private final PileService pileService;
    private final QueueService queueService;

    public ConfigController(
            ConfigService configService,
            AccountService accountService,
            ChargingService chargingService,
            PileService pileService,
            QueueService queueService
    ) {
        this.configService = configService;
        this.accountService = accountService;
        this.chargingService = chargingService;
        this.pileService = pileService;
        this.queueService = queueService;
    }

    @GetMapping("/config")
    public ApiResult<ConfigDtos.ConfigResponse> getConfig() {
        return ApiResult.ok(configService.currentConfigResponse());
    }

    @PostMapping("/config")
    public ApiResult<ConfigDtos.ConfigResponse> postConfig(@Valid @RequestBody ConfigDtos.UpdateConfigRequest request) {
        return ApiResult.ok(configService.initialize(request));
    }

    @PutMapping("/config")
    public ApiResult<ConfigDtos.ConfigResponse> putConfig(@Valid @RequestBody ConfigDtos.UpdateConfigRequest request) {
        return ApiResult.ok(configService.initialize(request));
    }

    @PostMapping("/demo/reset")
    public ApiResult<Boolean> reset() {
        configService.resetDemoData();
        return ApiResult.ok(true);
    }

    @PostMapping("/demo/seed")
    public ApiResult<ConfigDtos.SystemSnapshot> seed() {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(2, 3, 10, 2, 30.0, 10.0));
        seedCar("CAR-F-1", ChargeMode.FAST, 30.0);
        seedCar("CAR-F-2", ChargeMode.FAST, 45.0);
        seedCar("CAR-S-1", ChargeMode.SLOW, 20.0);
        seedCar("CAR-S-2", ChargeMode.SLOW, 30.0);
        return ApiResult.ok(snapshot());
    }

    @GetMapping("/demo/snapshot")
    public ApiResult<ConfigDtos.SystemSnapshot> getSnapshot() {
        return ApiResult.ok(snapshot());
    }

    private void seedCar(String carId, ChargeMode mode, double amount) {
        accountService.createNewAccount(carId, carId, 80.0);
        accountService.setPassword(carId, "123456");
        chargingService.submitRequest(carId, amount, mode);
    }

    private ConfigDtos.SystemSnapshot snapshot() {
        return new ConfigDtos.SystemSnapshot(
                configService.currentConfigResponse(),
                pileService.findAll(),
                queueService.queryQueueState(),
                List.<BillingDtos.BillResponse>of()
        );
    }
}
