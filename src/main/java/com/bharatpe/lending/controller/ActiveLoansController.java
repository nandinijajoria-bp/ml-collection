package com.bharatpe.lending.controller;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.LendingActiveLoansResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("lending")
public class ActiveLoansController {

    Logger logger = LoggerFactory.getLogger(ActiveLoansController.class);

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @RequestMapping(value = "/active_loans", method = RequestMethod.GET, consumes = "application/json", produces = "application/json")
    public ResponseEntity<LendingActiveLoansResponseDTO> getAvailableLoans(HttpServletRequest httpServletRequest,
            @RequestParam(name = "merchant_id", required = true) String requestMerchantId,
            @RequestParam(name = "merchant_store_id", required = false) String requestMerchantStoreId) {

        LendingActiveLoansResponseDTO responseDTO = new LendingActiveLoansResponseDTO();
        ResponseEntity<LendingActiveLoansResponseDTO> finalResponse;
        Long merchantId = requestMerchantId != null ? Long.parseLong(requestMerchantId) : null;
        Long merchantStoreId = requestMerchantStoreId != null ? Long.parseLong(requestMerchantStoreId) : null;
        List<LendingPaymentSchedule> activeLoans = fetchLendingPaymentSchedule(merchantId, merchantStoreId);
        if (activeLoans == null || activeLoans.isEmpty()) {
            responseDTO.setActiveLoans(Collections.emptyList());
            responseDTO.setMessage("No Active Loans Found");
            responseDTO.setSuccess(false);
        } else {
            responseDTO.setActiveLoansFromLendingPaymentSchedule(activeLoans);
            responseDTO.setMessage("Successfully fetched Active Loans");
            responseDTO.setSuccess(true);
        }
        finalResponse = new ResponseEntity<>(responseDTO, HttpStatus.OK);
        return finalResponse;

    }

    private List<LendingPaymentSchedule> fetchLendingPaymentSchedule(Long merchantId, Long merchantStoreId) {
        if (merchantStoreId != null) {
            return lendingPaymentScheduleDao.findByMerchantIdAndMerchantStoreIdAndStatus(merchantId, merchantStoreId,
                    "ACTIVE");
        }
        return lendingPaymentScheduleDao.findByMerchantIdAndStatusList(merchantId, "ACTIVE");
    }
}
