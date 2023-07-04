package com.bharatpe.lending.loanV2.controller;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.BankStatementSessionCallbackDto;
import com.bharatpe.lending.loanV2.dto.BankStatementUploadRequestDto;
import com.bharatpe.lending.loanV2.service.BankStatementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/lending")
@Slf4j
public class BankStatementController {

    @Autowired
    BankStatementService bankStatementService;

    @PostMapping(value = "/upload/bank-statement")
    public ResponseEntity<?> uploadBankStatement(
            @RequestBody BankStatementUploadRequestDto requestDto,
            @RequestAttribute(name = "merchant", required = false) BasicDetailsDto merchant
    ) {
        ApiResponse apiResponse = bankStatementService.uploadBankStatementFile(merchant.getId(), requestDto.getFileName(), requestDto.getPassword(), requestDto.getBankName(), requestDto.getBase64());
        if (!apiResponse.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);
        }
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);

    }

    @GetMapping(value = "/bank-statement/sessions/list")
    public ResponseEntity<?> getBankStatementSessionsList(
            @RequestAttribute(name = "merchant", required = false) BasicDetailsDto merchant
    ) {
        return ResponseEntity.ok(bankStatementService.bankStatementSessionsList(merchant.getId()));
    }

    @GetMapping(value = "/bank/list")
    public ResponseEntity<?> getBankList(
            @RequestParam(name = "bank_name") String bankName
    ) {
        return ResponseEntity.ok(bankStatementService.fetchBankList(bankName));
    }

    @PostMapping(value = "/bank-statement/session/callback")
    public ResponseEntity<?> bankStatementSessionCallback(
            @RequestBody BankStatementSessionCallbackDto bankStatementSessionCallbackDto
    ) {
        log.info("Callback received for sessionId : {}, {}", bankStatementSessionCallbackDto.getSessionId(), bankStatementSessionCallbackDto);
        bankStatementService.updateBankStatementSession(bankStatementSessionCallbackDto);
        return ResponseEntity.ok("Successfully updated bankStatement session status");
    }
}
