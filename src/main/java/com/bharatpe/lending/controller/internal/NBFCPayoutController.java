package com.bharatpe.lending.controller.internal;

import com.bharatpe.lending.common.entity.NBFCPayout;
import com.bharatpe.lending.service.NBFCPayoutService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("lending/internal/nbfcPayout")
@Slf4j
public class NBFCPayoutController {

    private final NBFCPayoutService nbfcPayoutService;

    public NBFCPayoutController(NBFCPayoutService nbfcPayoutService) {
        this.nbfcPayoutService = nbfcPayoutService;
    }

    @PostMapping(produces = "application/json")
    public ResponseEntity<Object> createTransaction(@RequestBody Map<String,Object> requestDto) {
        try {
            NBFCPayout payout = nbfcPayoutService.processPayout(Long.valueOf(String.valueOf(requestDto.get("id"))));
            return ResponseEntity.ok(payout);
        } catch (Exception ex) {
            log.warn(ex.getClass().getSimpleName() + " occurred while creating transaction request: {} - {}", requestDto, ex);
            return ResponseEntity.status(500).body(null);
        }
    }
}
