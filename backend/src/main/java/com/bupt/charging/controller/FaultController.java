package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.FaultDtos;
import com.bupt.charging.service.FaultService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/faults")
public class FaultController {
    private final FaultService faultService;

    public FaultController(FaultService faultService) {
        this.faultService = faultService;
    }

    @PostMapping
    public ApiResult<FaultDtos.FaultResult> fault(@Valid @RequestBody FaultDtos.CreateFaultRequest request) {
        return ApiResult.ok(faultService.handleFault(request.pileId(), request.strategy()));
    }

    @PostMapping("/{pileId}/recover")
    public ApiResult<FaultDtos.FaultResult> recover(@PathVariable String pileId) {
        return ApiResult.ok(faultService.recoverPile(pileId));
    }
}
