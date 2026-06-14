package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.ScenarioDtos;
import com.bupt.charging.service.AcceptanceScenarioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {
    private final AcceptanceScenarioService acceptanceScenarioService;

    public ScenarioController(AcceptanceScenarioService acceptanceScenarioService) {
        this.acceptanceScenarioService = acceptanceScenarioService;
    }

    @GetMapping("/course-sample")
    public ApiResult<ScenarioDtos.ReplayBundle> courseSampleDefinition() {
        return ApiResult.ok(acceptanceScenarioService.runCourseSampleReplay());
    }

    @PostMapping("/course-sample/run")
    public ApiResult<ScenarioDtos.ReplayBundle> runCourseSample() {
        return ApiResult.ok(acceptanceScenarioService.runCourseSampleReplay());
    }
}
