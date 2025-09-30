package com.bharatpe.lending.ai.controller;

import com.bharatpe.lending.ai.dto.LendingCollectionExcessDto;
import com.bharatpe.lending.ai.dto.LendingLedgerDto;
import com.bharatpe.lending.ai.services.CollectionService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("ai/collection")
public class CollectionDetailsAiController {


    @Autowired
    CollectionService collectionService;


    @GetMapping(value = "/ledger",produces = "application/json")
    public ResponseEntity<ApiResponse<List<List<LendingLedgerDto>>>> getLendingLedger(
            @RequestAttribute(required = false) BasicDetailsDto merchant,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String date) {

        if (merchant != null) {
            merchantId = merchant.getId();
        }

        if (merchantId == null) {
            log.info("merchantId is null");
            return ResponseEntity.ok(new ApiResponse<>(true, "merchantId is null"));
        }

        List<List<LendingLedgerDto>> ledgerResponse = collectionService.getLendingLedgerByMerchant(merchantId, date);
        String message = CollectionUtils.isEmpty(ledgerResponse) ? "no ledger records found" : "success";
        log.info("Fetched loan ledger details for merchantId: {} ledgerResponse: {}", merchantId, ledgerResponse);
        return ResponseEntity.ok(new ApiResponse<>(true, ledgerResponse, message));
    }

    @GetMapping(value = "/excess",produces = "application/json")
    public ResponseEntity<ApiResponse<List<List<LendingCollectionExcessDto>>>> getExcessDetails(
            @RequestAttribute(required = false) BasicDetailsDto merchant,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String date) {

        if (merchant != null) {
            merchantId = merchant.getId();
        }

        log.info("Request received to get excess details for merchantId: {}", merchantId);

        if (merchantId == null) {
            return ResponseEntity.ok(new ApiResponse<>(true, "merchantId is null"));
        }

        List<List<LendingCollectionExcessDto>> allExcessList =
                collectionService.getExcessDetailsByMerchant(merchantId, date);

        String message = CollectionUtils.isEmpty(allExcessList) ? "no excess records found" : "success";
        log.info("Fetched excess details for merchantId: {} allExcessList: {}", merchantId, allExcessList);
        return ResponseEntity.ok(new ApiResponse<>(true, allExcessList, message));
    }

}
