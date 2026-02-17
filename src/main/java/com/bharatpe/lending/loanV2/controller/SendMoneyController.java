package com.bharatpe.lending.loanV2.controller;

import com.bharatpe.common.entities.MerchantUser;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("send_money")
@Slf4j
public class SendMoneyController {

    @Autowired
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @RequestMapping("/sync_psp")
    public ApiResponseDTO syncPsp(@RequestAttribute BasicDetailsDto merchant, @RequestBody SendMoneyRequestDTO<SyncPspDTO> requestDTO) {
        log.info("sync_psp for merchant user: {}, requestDTO {}", merchant.getId(), requestDTO);

        loanDetailsServiceV2.saveMerchantPspInMongoV2(requestDTO, merchant);
        ApiResponseDTO apiResponseDTO = new ApiResponseDTO();
        apiResponseDTO.setSuccess(true);
        log.info("sync_psp response: {} for merchant user: {}", apiResponseDTO, merchant.getId());
        return apiResponseDTO;
    }
}