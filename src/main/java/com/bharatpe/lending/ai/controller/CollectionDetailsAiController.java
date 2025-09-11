package com.bharatpe.lending.ai.controller;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Slf4j
@RestController
public class CollectionDetailsAiController {

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingCollectionExcessDao lendingCollectionExcessDao;


    @GetMapping(value = "/ledger",produces = "application/json")
    public ResponseEntity<ApiResponse<List<LendingLedger>>> getApplicationDetail(
            @RequestAttribute(required = false) BasicDetailsDto merchant,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String date) {
        if(merchant!=null){
            merchantId=merchant.getId();
        }
        log.info("Request received to get loan application details for merchantId: {}", merchantId);
        Date parsedDate = getParsedDate(date);
        if(parsedDate==null){
            log.info("Invalid date format provided: {}", date);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Invalid date format. Please use 'yyyy-MM-dd'."));
        }
        List<LendingLedger> lendingLedgerList = lendingLedgerDao.findByMerchantIdAndDateOrderByDateDesc(merchantId,parsedDate);
        if(lendingLedgerList==null){
            log.info("No loan application details found for merchantId: {}", merchantId);
            return ResponseEntity.ok(new ApiResponse<>(true, "no loan application found"));
        }
        log.info("Fetched loan application details for merchantId: {}, details are: {}", merchantId, lendingLedgerList);
        return ResponseEntity.ok(new ApiResponse<>(lendingLedgerList));
    }

    @GetMapping(value = "/excess", produces = "application/json")
    public ResponseEntity<ApiResponse<List<LendingCollectionExcess>>> getExcessDetails(
            @RequestAttribute(required = false) BasicDetailsDto merchant,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Long loanId) {

        if (merchant != null) {
            merchantId = merchant.getId();
        }
        log.info("Request received to get excess details for merchantId: {}", merchantId);


        List<LendingCollectionExcess> excessList =
                lendingCollectionExcessDao.findByMerchantIdAndLoanIdOrderByIdAsc(merchantId, loanId);

        if (excessList == null || excessList.isEmpty()) {
            log.info("No excess details found for merchantId: {}", merchantId);
            return ResponseEntity.ok(new ApiResponse<>(true, "no excess records found"));
        }

        log.info("Fetched excess details for merchantId: {}, details: {}", merchantId, excessList);
        return ResponseEntity.ok(new ApiResponse<>(excessList));
    }



    private Date getParsedDate(String date) {
        Date parsedDate = null;
        if (date != null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd"); // adjust format as needed
                parsedDate = sdf.parse(date);
            } catch (Exception e) {
                return null;
            }
        }
        return parsedDate;
    }

}
