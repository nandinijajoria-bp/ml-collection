package com.bharatpe.lending.ai.controller;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.ai.dto.LendingCollectionExcessDto;
import com.bharatpe.lending.ai.dto.LendingLedgerDto;
import com.bharatpe.lending.ai.services.CollectionService;
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("ai/collection")
public class CollectionDetailsAiController {

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingCollectionExcessDao lendingCollectionExcessDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    CollectionService collectionService;


    @GetMapping(produces = "application/json")
    public ResponseEntity<ApiResponse<List<List<LendingLedgerDto>>>> getLendingLedger(
            @RequestAttribute(required = false) BasicDetailsDto merchant,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Long days) {

        if (merchant != null) {
            merchantId = merchant.getId();
        }

        if (merchantId == null) {
            log.info("merchantId is null");
            return ResponseEntity.ok(new ApiResponse<>(true, "merchantId is null"));
        }

        List<List<LendingLedgerDto>> ledgerResponse = collectionService.getLendingLedgerByMerchant(merchantId, days);

        if (ledgerResponse.isEmpty()) {
            log.info("No ledger records found for merchantId: {}", merchantId);
            return ResponseEntity.ok(new ApiResponse<>(true, "no ledger records found"));
        }

        log.info("Fetched loan ledger details for merchantId: {}", merchantId);
        return ResponseEntity.ok(new ApiResponse<>(ledgerResponse));
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<ApiResponse<List<List<LendingCollectionExcessDto>>>> getExcessDetails(
            @RequestAttribute(required = false) BasicDetailsDto merchant,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Long days) {

        if (merchant != null) {
            merchantId = merchant.getId();
        }

        log.info("Request received to get excess details for merchantId: {}", merchantId);

        if (merchantId == null) {
            return ResponseEntity.ok(new ApiResponse<>(true, "merchantId is null"));
        }

        List<List<LendingCollectionExcessDto>> allExcessList =
                collectionService.getExcessDetailsByMerchant(merchantId, days);

        if (allExcessList.isEmpty()) {
            log.info("No excess details found for merchantId: {}", merchantId);
            return ResponseEntity.ok(new ApiResponse<>(true, "no excess records found"));
        }

        log.info("Fetched excess details for merchantId: {}", merchantId);
        return ResponseEntity.ok(new ApiResponse<>(allExcessList));
    }

}
