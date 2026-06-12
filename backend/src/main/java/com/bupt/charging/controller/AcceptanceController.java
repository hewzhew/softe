package com.bupt.charging.controller;

import com.bupt.charging.dto.AcceptanceDtos;
import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.service.AcceptanceScenarioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/acceptance")
public class AcceptanceController {
    private final AcceptanceScenarioService acceptanceScenarioService;

    public AcceptanceController(AcceptanceScenarioService acceptanceScenarioService) {
        this.acceptanceScenarioService = acceptanceScenarioService;
    }

    @GetMapping("/scenario")
    public ApiResult<AcceptanceDtos.AcceptanceScenarioResponse> scenario() {
        return ApiResult.ok(acceptanceScenarioService.runDefaultScenario());
    }
}
