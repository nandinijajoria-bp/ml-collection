package com.bharatpe.lending.controller;

import com.bharatpe.lending.dto.QrStatusEventDTO;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.service.QrStatusApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("lending")
public class QrStatusController {

    private final QrStatusApiService qrStatusApiService;

    public QrStatusController(QrStatusApiService qrStatusApiService) {
        this.qrStatusApiService = qrStatusApiService;
    }

    @PostMapping("/qr-status/webhook")
    public ResponseEntity<ApiResponse<?>> handleQrStatusEvent(@RequestBody QrStatusEventDTO eventDTO) {
        ApiResponse<?> response = qrStatusApiService.handleQrStatusEvent(eventDTO);
        return ResponseEntity.ok(response);
    }
}