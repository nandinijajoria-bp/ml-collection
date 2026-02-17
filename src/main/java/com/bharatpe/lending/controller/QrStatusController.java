package com.bharatpe.lending.controller;

import com.bharatpe.lending.dto.MerchantStatusDTO;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.service.QrStatusApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("lending")
public class QrStatusController {

    private final QrStatusApiService qrStatusApiService;

    public QrStatusController(QrStatusApiService qrStatusApiService) {
        this.qrStatusApiService = qrStatusApiService;
    }

    @PostMapping("/qr-status/webhook")
    public ResponseEntity<Map<String, ApiResponse<?>>> handleCustomQrStatusWebhook(
            @RequestBody Map<String, MerchantStatusDTO> merchantStatusMap) {
        Map<String, ApiResponse<?>> result = qrStatusApiService.handleCustomQrStatusWebhook(merchantStatusMap);
        return ResponseEntity.ok(result);
    }
}