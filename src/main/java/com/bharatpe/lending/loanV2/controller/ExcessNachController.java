package com.bharatpe.lending.loanV2.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.service.ExcessNachService;
import com.bharatpe.lending.service.RefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/lending")
@Slf4j
public class ExcessNachController {

    @Autowired
    ExcessNachService excessNachService;

    @GetMapping(value = "/excess-nach/list")
    public ResponseEntity<?> excessNachList(
            @RequestAttribute BasicDetailsDto merchant
    ) {
        return ResponseEntity.ok(excessNachService.excessNachDetailsList(merchant.getId()));
    }

    @GetMapping(value = "/excess-nach/details")
    public ResponseEntity<?> excessNachDetails(
            @RequestParam(name = "terminal_order_id") String terminalOrderId,
            @RequestAttribute BasicDetailsDto merchant
    ) {
        ApiResponse apiResponse = excessNachService.ExcessNachDetailsForTerminalOrderId(terminalOrderId);
        if(apiResponse.isSuccess()) {
            return ResponseEntity.ok(apiResponse);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
    }

    @GetMapping(value = "/excess-nach/amount")
    public ResponseEntity<?> excessNachAmount(
            @RequestAttribute BasicDetailsDto merchant
    ) {
        return ResponseEntity.ok(excessNachService.getExcessNachAmount(merchant.getId()));
    }
}
