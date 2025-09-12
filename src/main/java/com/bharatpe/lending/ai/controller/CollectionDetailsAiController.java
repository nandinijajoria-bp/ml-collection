package com.bharatpe.lending.ai.controller;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
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


    @GetMapping(value = "/ledger",produces = "application/json")
    public ResponseEntity<ApiResponse<List<List<LendingLedger>>>> getLendingLedger(
            @RequestAttribute(required = false) BasicDetailsDto merchant,
            @RequestParam(required = false) Long merchantId) {
        if(merchant!=null){
            merchantId=merchant.getId();
        }
        if(merchantId == null)
        {
            log.info("merchant is null : {}", merchantId);
            return ResponseEntity.ok(new ApiResponse<>(true, "merchantId is null"));
        }
        List<LendingPaymentSchedule> lendingPaymentScheduleList = lendingPaymentScheduleDao.findAllLendingPaymentScheduleByMerchantId(merchantId);
        if (lendingPaymentScheduleList.isEmpty()) {
            log.info("No active loan found for merchantId: {}", merchantId);
            return ResponseEntity.ok(new ApiResponse<>(true, "no  loan found"));
        }

        List<List<LendingLedger>> allLendingLedgerList = new ArrayList<>();
        for(LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
            List<LendingLedger> lendingLedgerList = lendingLedgerDao.findByLendingPaymentScheduleOrderByDateAsc(lendingPaymentSchedule);
            if(!lendingLedgerList.isEmpty()){
                allLendingLedgerList.add(lendingLedgerList);
            }
        }
        if(allLendingLedgerList.isEmpty()){
            log.info("No ledger records found for merchantId: {}", merchantId);
            return ResponseEntity.ok(new ApiResponse<>(true, "no ledger records found"));
        }
        log.info("Fetched loan application details for merchantId: {}, details are: {}", merchantId, allLendingLedgerList);
        return ResponseEntity.ok(new ApiResponse<>(allLendingLedgerList));
    }

    @GetMapping(value = "/excess", produces = "application/json")
    public ResponseEntity<ApiResponse<List<List<LendingCollectionExcess>>>> getExcessDetails(
            @RequestAttribute(required = false) BasicDetailsDto merchant,
            @RequestParam(required = false) Long merchantId) {

        if (merchant != null) {
            merchantId = merchant.getId();
        }
        log.info("Request received to get excess details for merchantId: {}", merchantId);
        if(merchantId == null)
        {
            log.info("merchant is null : {}", merchantId);
            return ResponseEntity.ok(new ApiResponse<>(true, "merchantId is null"));
        }

        List<LendingPaymentSchedule> lendingPaymentScheduleList = lendingPaymentScheduleDao.findAllLendingPaymentScheduleByMerchantId(merchantId);
        if (lendingPaymentScheduleList.isEmpty()) {
            log.info("No active loan found for merchantId: {}", merchantId);
            return ResponseEntity.ok(new ApiResponse<>(true, "no  loan found"));
        }

        List<List<LendingCollectionExcess>> allExcessList = new ArrayList<>();
        for(LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
            List<LendingCollectionExcess> excessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdOrderByIdAsc(merchantId, lendingPaymentSchedule.getId());
            if(!excessList.isEmpty()){
                allExcessList.add(excessList);
            }
        }



        if ( allExcessList.isEmpty()) {
            log.info("No excess details found for merchantId: {}", merchantId);
            return ResponseEntity.ok(new ApiResponse<>(true, "no excess records found"));
        }

        log.info("Fetched excess details for merchantId: {}, details: {}", merchantId, allExcessList);
        return ResponseEntity.ok(new ApiResponse<>(allExcessList));
    }

}
