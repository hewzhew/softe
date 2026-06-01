package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.PileDtos;
import com.bupt.charging.service.PileService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/piles")
public class PileController {
    private final PileService pileService;

    public PileController(PileService pileService) {
        this.pileService = pileService;
    }

    @GetMapping
    public ApiResult<List<PileDtos.PileStateResponse>> all() {
        return ApiResult.ok(pileService.findAll());
    }

    @GetMapping("/{pileId}")
    public ApiResult<PileDtos.PileStateResponse> one(@PathVariable String pileId) {
        return ApiResult.ok(pileService.toResponse(pileService.requirePile(pileId)));
    }

    @PostMapping("/{pileId}/power-on")
    public ApiResult<PileDtos.PileStateResponse> powerOn(@PathVariable String pileId) {
        return ApiResult.ok(pileService.powerOn(pileId));
    }

    @PostMapping("/{pileId}/start")
    public ApiResult<PileDtos.PileStateResponse> start(@PathVariable String pileId) {
        return ApiResult.ok(pileService.startPile(pileId));
    }

    @PostMapping("/{pileId}/power-off")
    public ApiResult<PileDtos.PileStateResponse> powerOff(@PathVariable String pileId) {
        return ApiResult.ok(pileService.powerOff(pileId));
    }
}
