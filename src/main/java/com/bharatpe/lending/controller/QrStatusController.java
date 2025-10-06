package com.bharatpe.lending.controller;

import com.bharatpe.lending.dto.QrStatusEventDTO;
import com.bharatpe.lending.service.QrStatusApiService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("lending")
public class QrStatusController {

    private final QrStatusApiService qrStatusApiService;

    public QrStatusController(QrStatusApiService qrStatusApiService) {
        this.qrStatusApiService = qrStatusApiService;
    }

    @PostMapping("/qr-status")
    public String handleQrStatusEvent(@RequestBody QrStatusEventDTO eventDTO) {
        return qrStatusApiService.handleQrStatusEvent(eventDTO);
    }
}