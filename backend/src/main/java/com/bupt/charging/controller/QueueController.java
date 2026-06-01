package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.QueueDtos;
import com.bupt.charging.service.QueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queues")
public class QueueController {
    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping
    public ApiResult<QueueDtos.QueueStateResponse> queues() {
        return ApiResult.ok(queueService.queryQueueState());
    }
}
