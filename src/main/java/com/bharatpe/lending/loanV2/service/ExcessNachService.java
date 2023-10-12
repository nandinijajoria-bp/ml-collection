package com.bharatpe.lending.loanV2.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingCollectionExcessDao;
import com.bharatpe.lending.common.entity.LendingCollectionExcess;
import com.bharatpe.lending.common.query.dao.LendingRefundLedgerSlaveDao;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.LendingRefundLedgerDao;
import com.bharatpe.lending.dto.RepaymentHistoryDTO;
import com.bharatpe.lending.entity.LendingRefundLedger;
import com.bharatpe.lending.loanV2.dto.ApiResponse;
import com.bharatpe.lending.loanV2.dto.ExcessNachDetailDTO;
import com.bharatpe.lending.loanV3.revamp.dto.LoanDashboardResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ExcessNachService {

    @Autowired
    LendingRefundLedgerDao lendingRefundLedgerDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingRefundLedgerSlaveDao lendingRefundLedgerSlaveDao;

    @Autowired
    LendingCollectionExcessDao lendingCollectionExcessDao;

    public ApiResponse<?> excessNachDetailsList(Long merchantId) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if(ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for merchantId : {}", merchantId);
                return new ApiResponse<>(new ArrayList<>());
            }
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(lendingApplication.getId());
            if(ObjectUtils.isEmpty(lendingPaymentSchedule)) {
                log.info("No paymentSchedule found for applicationId : {} for merchantId : {}", lendingApplication.getId(), merchantId);
                return new ApiResponse<>(new ArrayList<>());
            }
            List<LendingRefundLedger> lendingRefundLedgers = lendingRefundLedgerDao.findByMerchantIdAndLoanIdAndAdjustmentModeOrderByIdDesc(merchantId, lendingPaymentSchedule.getId(), "EXCESS_NACH");
            if (ObjectUtils.isEmpty(lendingRefundLedgers)) {
                log.info("No excess nach ledger found for merchant : {}", merchantId);
                return new ApiResponse<>(new ArrayList<>());
            }
            List<ExcessNachDetailDTO> excessNachDetailDTOS = new ArrayList<>();
            for(LendingRefundLedger ledger : lendingRefundLedgers) excessNachDetailDTOS.add(ExcessNachDetailDTO.from(ledger));
            return new ApiResponse<>(excessNachDetailDTOS);
        } catch (Exception e) {
            log.error("Exception in getting excess nach details list for merchantId : {}, {}", merchantId, e.getMessage());
            return new ApiResponse<>(new ArrayList<>());
        }
    }

    public ApiResponse<?> ExcessNachDetailsForTerminalOrderId(String terminalOrderId) {
        try {
            LendingRefundLedger ledger = lendingRefundLedgerDao.findByTerminalOrderId(terminalOrderId);
            if(ObjectUtils.isEmpty(ledger)) {
                log.info("No refund ledger found for terminalOrderId : {}", terminalOrderId);
                return new ApiResponse<>(false, "No refund ledger found for terminalOrderId : " + terminalOrderId);
            }
            MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(ledger.getMerchantId());
            if(ObjectUtils.isEmpty(merchantDetailsDto)) {
                log.info("Merchant details not found for merchantId : {}", ledger.getMerchantId());
                return new ApiResponse<>(false, "Merchant details not found for merchantId : " + ledger.getMerchantId());
            }
            Map<String, Object> data = new HashMap<>();
            data.put("ledger", ledger);
            data.put("beneficiary_name", merchantDetailsDto.getBankDetail().getBeneficiaryName());
            return new ApiResponse<>(data);
        } catch (Exception e) {
            log.error("Exception getting excess nach detail for terminalOrderId : {}, {}", terminalOrderId, e.getMessage());
            return new ApiResponse<>(false, e.getMessage());
        }
    }

    public Double getExcessNachAmount(Long merchantId) {
        try {
            Double excessNachAmount = 0D;
            LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for merchantId : {}", merchantId);
                return excessNachAmount;
            }
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(lendingApplication.getId());
            if (ObjectUtils.isEmpty(lendingPaymentSchedule)) {
                log.info("No paymentSchedule found for applicationId : {} for merchantId : {}", lendingApplication.getId(), merchantId);
                return excessNachAmount;
            }
            excessNachAmount = lendingRefundLedgerSlaveDao.findTotalExcessNachAmount(merchantId, lendingPaymentSchedule.getId(), "EXCESS_NACH", "PENDING");
            return excessNachAmount;
        } catch (Exception e) {
            log.error("Exception getting pending excessNach amount for merchantId : {}, {}", merchantId, e.getMessage());
            return 0D;
        }
    }

    public void setExcessCollectionDetails(Long merchantId, LoanDashboardResponse loanDashboardResponse) {
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for merchantId : {}", merchantId);
                return;
            }
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByApplicationId(lendingApplication.getId());
            if (ObjectUtils.isEmpty(lendingPaymentSchedule)) {
                log.info("No paymentSchedule found for applicationId : {} for merchantId : {}", lendingApplication.getId(), merchantId);
                return;
            }
            Double excessNachAmountRefundable = lendingRefundLedgerSlaveDao.findTotalExcessNachAmount(merchantId, lendingPaymentSchedule.getId(), "EXCESS_NACH", "PENDING");
            loanDashboardResponse.setExcessNachAmount(excessNachAmountRefundable);

            List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdOrderByIdAsc(lendingPaymentSchedule.getMerchantId(), lendingPaymentSchedule.getId());
            Double excessCollectedAmount = 0D;
            Double excessCollectionAdjusted = 0D;
            Double excessCollectionBalance = 0D;
            if(ObjectUtils.isEmpty(lendingCollectionExcessList)){
                log.info("No excess collection for merchantId : {}", merchantId);
                return;
            }
            for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
                excessCollectedAmount += lendingCollectionExcess.getExcessNachCreditAmount();
                excessCollectionBalance += lendingCollectionExcess.getAmount();
                excessCollectionAdjusted = excessCollectedAmount - excessCollectionBalance;
            }
            loanDashboardResponse.setExcessCollectionAmount(excessCollectedAmount);
            loanDashboardResponse.setExcessCollectionBalance(excessCollectionBalance);
            loanDashboardResponse.setExcessCollectionAdjusted(excessCollectionAdjusted);
        } catch (Exception e) {
            log.error("Exception getting pending excessNach amount for merchantId : {}, {}", merchantId, e.getMessage());
            return;
        }
    }

    public void setExcessCollectionDetails(Long merchantId, RepaymentHistoryDTO repaymentHistoryDTO, Long loanId) {
        try {
            List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdOrderByIdAsc(merchantId, loanId);
            Double excessCollectedAmount = 0D;
            Double excessCollectionAdjusted = 0D;
            Double excessCollectionBalance = 0D;
            if(ObjectUtils.isEmpty(lendingCollectionExcessList)){
                log.info("No excess collection for merchantId : {}", merchantId);
                return;
            }
            for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
                excessCollectedAmount += lendingCollectionExcess.getExcessNachCreditAmount();
                excessCollectionBalance += lendingCollectionExcess.getAmount();
                excessCollectionAdjusted = excessCollectedAmount - excessCollectionBalance;
            }
            repaymentHistoryDTO.setExcessCollectionAmount(excessCollectedAmount);
            repaymentHistoryDTO.setExcessCollectionBalance(excessCollectionBalance);
            repaymentHistoryDTO.setExcessCollectionAdjusted(excessCollectionAdjusted);
        } catch (Exception e) {
            log.error("Exception getting pending excessNach amount for merchantId : {}, {}", merchantId, e.getMessage());
        }
    }

    public Double getExcessCollectionBalanceAmount(Long merchantId, Long loanId) {
        try {
            List<LendingCollectionExcess> lendingCollectionExcessList = lendingCollectionExcessDao.findByMerchantIdAndLoanIdAndStatusOrderByIdAsc(merchantId, loanId, "ACTIVE");
            Double excessCollectionBalance = 0D;
            for(LendingCollectionExcess lendingCollectionExcess : lendingCollectionExcessList){
                if(lendingCollectionExcess.getAmount() > 0){
                    excessCollectionBalance += lendingCollectionExcess.getAmount();
                }
            }
            return excessCollectionBalance;
        } catch (Exception e) {
            log.error("Exception getting pending balance amount from excess collection for merchantId : {}, {}", merchantId, e.getMessage());
            return null;
        }
    }
}
