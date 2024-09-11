package com.bharatpe.lending.collection.core.service.impl;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.common.enums.Status;
import com.bharatpe.lending.collection.core.service.LoanClosurePostingService;
import com.bharatpe.lending.collection.core.service.LoanClosureService;
import com.bharatpe.lending.collection.core.utils.LoanPaymentUtil;
import com.bharatpe.lending.common.dao.ForeClosureAmountInfoDao;
import com.bharatpe.lending.common.dao.LoanForeClosureChargesDao;
import com.bharatpe.lending.common.dto.NotificationPayloadDto;
import com.bharatpe.lending.common.entity.LoanForeClosureCharges;
import com.bharatpe.lending.common.service.NBFCService;
import com.bharatpe.lending.common.entity.ForeClosureAmountInfo;
import com.bharatpe.lending.common.service.SherlocLoanStatusChangeService;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.service.RedisNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class LoanClosureServiceImpl implements LoanClosureService {

    @Autowired
    LoanClosurePostingService loanClosurePostingService;
    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;
    @Autowired
    LoanForeClosureChargesDao loanForeClosureChargesDao;
    @Autowired
    SherlocLoanStatusChangeService sherlocLoanStatusChangeService;
    @Autowired
    NBFCService nbfcService;
    @Autowired
    LoanPaymentUtil loanPaymentUtil;
    ExecutorService notificationExecutor = Executors.newFixedThreadPool(10);

    @Autowired
    ForeClosureAmountInfoDao foreClosureAmountInfoDao;

    @Override
    public void closeLoanAndUpdateLender(LendingPaymentSchedule loan, LendingLedger lendingLedger, Long orderId) {
        log.info("inside close loan and update lender for loanId {} ",loan.getId());
        loan = closeLoanAndUpdateStatus(loan);
        updateForeclosureAmountInfoLedgerId(lendingLedger, orderId,loan.getId());
        log.info("posting closure status to lender for loanId {} and loan-details {}",loan.getId(),loan);
        postClosureStatusToLender( loan,  lendingLedger,  orderId);
    }

    private void updateForeclosureAmountInfoLedgerId(LendingLedger lendingLedger, Long orderId, Long loanId) {
        ForeClosureAmountInfo foreClosureAmountInfo = foreClosureAmountInfoDao.findByOrderId(orderId);
        if(foreClosureAmountInfo != null && lendingLedger != null)
        {
            try {
                log.info("going to update foreclosureamountinfo ledgerId for foreclosureamountinfo {} and ledger {}", foreClosureAmountInfo.getId(), lendingLedger.getId());
                foreClosureAmountInfo.setLedgerId(lendingLedger.getId());
                foreClosureAmountInfoDao.save(foreClosureAmountInfo);
            }catch (Exception e){
                log.error("error occured while saving ledgerId for loanID {} in foreclosure amount info",loanId);
            }
        }
    }


    private void postClosureStatusToLender(LendingPaymentSchedule activeLoan, LendingLedger lendingLedger, Long orderId) {

        if (activeLoan.getStatus().equalsIgnoreCase(Status.LendingStatus.CLOSED.toString())) {
            if ("LDC".equals(activeLoan.getNbfc())) {
                nbfcService.pushCloseLoanEventToKafka(activeLoan.getApplicationId());
            }
        }
        boolean isLoanClosed = "CLOSED".equalsIgnoreCase(activeLoan.getStatus());

        Double finalAmount = lendingLedger.getAmount();
        notificationExecutor.execute(() -> loanPaymentUtil.sendSMS(activeLoan, finalAmount, isLoanClosed));

        if (activeLoan.getNbfc().equalsIgnoreCase(Lender.ABFL.name())) {
            loanClosurePostingService.sendForeclosureEvent(activeLoan.getApplicationId(), activeLoan.getMobile(), lendingLedger);
        } else if (activeLoan.getNbfc().equalsIgnoreCase(Lender.PIRAMAL.name())) {
            loanClosurePostingService.postForeclosureReceiptPiramal(activeLoan, lendingLedger);
        } else if (Arrays.asList("USFB", "CAPRI").contains(activeLoan.getNbfc())) {
            loanClosurePostingService.postForeclosureReceipt(activeLoan, lendingLedger);
        } else if (Lender.TRILLIONLOANS.name().equalsIgnoreCase(activeLoan.getNbfc())) {
            loanClosurePostingService.sendForeclosureEventTrillionLoans(activeLoan.getApplicationId(), lendingLedger, orderId);
        } else if (Lender.PAYU.name().equalsIgnoreCase(activeLoan.getNbfc())) {
            loanClosurePostingService.sendForeclosureEventPayu(activeLoan.getApplicationId(), lendingLedger, orderId);
        } else if (Lender.LIQUILOANS_P2P.name().equalsIgnoreCase(activeLoan.getNbfc())
                || Lender.LIQUILOANS_P2P_OF.name().equalsIgnoreCase(activeLoan.getNbfc())
                || Lender.LIQUILOANS_NBFC.name().equalsIgnoreCase(activeLoan.getNbfc())) {
            loanClosurePostingService.sendForeclosureChargesEventLiquiLoans(activeLoan.getApplicationId(), activeLoan.getId(), lendingLedger.getId(), activeLoan.getNbfc(), orderId);
        }
        Long merchantId = activeLoan.getMerchantId();
        log.info("sending loan flag event in adjustLoanBalance for merchantId : {}",merchantId);
        sherlocLoanStatusChangeService.pushLoanStatusChangeEventToKafka(merchantId, activeLoan.getStatus());
    }

    public void updateForeclosureChargesStatus(String status, Long orderId) {
        log.info("Going to update foreclosure charges status  orderid : {} and status {} ", orderId, status);
        LoanForeClosureCharges charge = loanForeClosureChargesDao.findByOrderId(orderId);
        if (charge != null) {
            charge.setStatus(status);
            loanForeClosureChargesDao.save(charge);
            log.info("updated the status of foreclosurecharges order : {} and status : {}", orderId , status);
        } else {
            log.info("no foreclosure charges for order : {} and status : {}", orderId , status);
        }
    }

    private LendingPaymentSchedule closeLoanAndUpdateStatus(LendingPaymentSchedule loan) {
        log.info("update status closed for loanId {} and loanDetails {}",loan.getId(),loan);
        loan.setStatus("CLOSED");
        loan.setDueAmount(0.0);
        loan.setDueInterest(0.0);
        loan.setDuePrinciple(0.0);
        loan.setDueOtherCharges(0.0);
        loan.setDuePenalty(0.0);
        loan.setEdiRemainingCount(0);
        loan.setClosingDate(new Date());
        //TODO : do we need to check next edi_date also
        lendingPaymentScheduleDao.save(loan);
        return loan;
    }
}
