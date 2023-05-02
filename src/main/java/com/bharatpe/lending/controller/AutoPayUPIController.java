package com.bharatpe.lending.controller;


import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.service.AutoPayUPIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auto-pay")
@Slf4j
public class AutoPayUPIController {

    @Autowired
    AutoPayUPIService autoPayUPIService;

    @PostMapping(value = "/register-mandate")
    public UPIRegisterResponseDto registerAutoPayForMerchant(
            @RequestAttribute BasicDetailsDto merchant,
            @RequestBody RequestDTO<UPIRegisterRequestDto> requestDTO) {
//        BasicDetailsDto merchant = new BasicDetailsDto();
//        Long applicationId = 7890L;
//        merchant.setId(12344L);
        return autoPayUPIService.registerUPI(merchant,requestDTO.getPayload().getLoanId(), requestDTO);

    }


    @GetMapping(value="/status-check/mandate")
    public MandateUPIStatusResponse statusCheckMandate(
            @RequestAttribute BasicDetailsDto merchant,
           @RequestParam String orderId)
    {
//        BasicDetailsDto merchant = new BasicDetailsDto();
//        merchant.setId(1234L);
//        orderId="Auto-UPI12";
        return autoPayUPIService.checkStatus(merchant,orderId);
    }






}
