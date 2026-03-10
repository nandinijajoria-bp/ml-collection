package com.bharatpe.lending.controller;

import com.bharatpe.lending.common.dto.LoanRestructureDto;
import com.bharatpe.lending.common.dto.LoanRestrutureResponseDto;
import com.bharatpe.lending.service.impl.CollectionServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/collection")
@Slf4j
public class CollectionController {

    @Autowired
    private CollectionServiceImpl collectionService;

    @PostMapping(value = "/loan-restructure/initiate")
    public ResponseEntity<LoanRestrutureResponseDto> initiateLoanRestructuring(@RequestBody LoanRestructureDto loanRestructureDto) {
        log.info("Received request for loan restructuring, loanRestructureDto: {}", loanRestructureDto);

        LoanRestrutureResponseDto response = collectionService.initiateLoanRestructuring(loanRestructureDto);

        if (response == null) {
            log.error("Loan restructuring initiation failed for requestId: {}", loanRestructureDto.getRequestId());
            return ResponseEntity.status(500).body(LoanRestrutureResponseDto.builder()
                    .success(false)
                    .message("Failed to initiate loan restructuring")
                    .status("FAILED")
                    .data(null)
                    .build());
        }
        if (response.isSuccess()) {
            log.info("Loan restructuring initiated successfully for requestId: {}", loanRestructureDto.getRequestId());
            return new ResponseEntity<>(response, HttpStatus.OK);
        } else {
            log.info("Loan restructuring initiation returned unsuccessful for requestId: {}, message: {}", loanRestructureDto.getRequestId(), response.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(value = "/loan-restructure/check-status")
    public ResponseEntity<LoanRestrutureResponseDto> getStatusForLoanRestructuring(
            @RequestParam(value = "applicationId") long applicationId,
            @RequestParam(value = "lan") long lan,
            @RequestParam(value = "requestId") long requestId){
        log.info("Received request for loan restructure status check, lan: {}, requestId: {}", lan, requestId);

        LoanRestrutureResponseDto response = collectionService.getStatusForLoanRestructuring(applicationId, lan, requestId);

        if (response == null) {
            log.error("Failed to fetch loan restructuring status for requestId: {}", requestId);
            return ResponseEntity.status(500).body(LoanRestrutureResponseDto.builder()
                    .success(false)
                    .message("Failed to fetch loan restructuring status")
                    .status("FAILED")
                    .data(null)
                    .build());
        }
        if (response.isSuccess()) {
            log.info("Loan restructuring status fetched successfully for requestId: {}", requestId);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } else {
            log.info("Loan restructuring status fetch returned unsuccessful for requestId: {}, message: {}", requestId, response.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }
}
