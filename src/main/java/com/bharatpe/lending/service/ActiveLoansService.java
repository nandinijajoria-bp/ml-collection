package com.bharatpe.lending.service;

import java.util.Collections;
import java.util.List;

import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.LendingActiveLoansResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ActiveLoansService {

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    public LendingActiveLoansResponseDTO getActiveLoans(Long merchantId, Long merchantStoreId) {
        LendingActiveLoansResponseDTO responseDTO = new LendingActiveLoansResponseDTO();
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
        return responseDTO;
    }

    private List<LendingPaymentSchedule> fetchLendingPaymentSchedule(Long merchantId, Long merchantStoreId) {
        if (merchantStoreId != null) {
            return lendingPaymentScheduleDao.findByMerchantIdAndMerchantStoreIdAndStatus(merchantId, merchantStoreId,
                    "ACTIVE");
        }
        return lendingPaymentScheduleDao.findByMerchantIdAndStatusList(merchantId, "ACTIVE");
    }
}
