package com.bharatpe.lending.service;

import java.math.BigInteger;
import java.util.*;

import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.LendingEDIScheduleDao;
import com.bharatpe.common.dao.MerchantSummaryDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.LoanDpdDao;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.util.LoanCalculationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MerchantLoansService {

    private Logger logger = LoggerFactory.getLogger(MerchantLoansService.class);

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LoanDpdDao loanDpdDao;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    @Autowired
    LendingCategoryDao lendingCategoryDao;

    @Autowired
    MerchantSummaryDao merchantSummaryDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingEDIScheduleDao lendingEDIScheduleDao;

    public LendingActiveLoansResponseDTO getActiveLoans(Long merchantId, Long merchantStoreId) {
        LendingActiveLoansResponseDTO responseDTO = new LendingActiveLoansResponseDTO();
        List<LendingPaymentSchedule> activeLoans = fetchLendingPaymentSchedule(merchantId, merchantStoreId, "ACTIVE");
        if (activeLoans == null || activeLoans.isEmpty()) {
            logger.info("No active loans found for merchantId: {}, merchantStoreId: {}", merchantId, merchantStoreId);
            responseDTO.setActiveLoans(Collections.emptyList());
            responseDTO.setMessage("No Active Loans Found");
            responseDTO.setSuccess(false);
        } else {
            logger.info("{} active loans found for merchantId: {}, merchantStoreId: {}", activeLoans.size(), merchantId, merchantStoreId);
            responseDTO.setActiveLoansFromLendingPaymentSchedule(activeLoans);
            responseDTO.setMessage("Successfully fetched Active Loans");
            responseDTO.setSuccess(true);
        }
        return responseDTO;
    }

    private List<LendingPaymentSchedule> fetchLendingPaymentSchedule(Long merchantId, Long merchantStoreId, String status) {
        if (merchantStoreId != null) {
            return lendingPaymentScheduleDao.findByMerchantIdAndMerchantStoreIdAndStatus(merchantId, merchantStoreId,
                    status);
        }
        return lendingPaymentScheduleDao.findByMerchantIdAndStatusList(merchantId, status);
    }
    public LendingMerchantLoansResponseDTO getMerchantLoans(Long merchantId) {
        LendingMerchantLoansResponseDTO responseDTO = new LendingMerchantLoansResponseDTO();
        responseDTO.setTopup(Boolean.FALSE);
        List<LendingPaymentSchedule> merchantLoans = lendingPaymentScheduleDao.findByMerchantIdAndCreditLoan(merchantId, false);
        if (merchantLoans == null || merchantLoans.isEmpty()) {
            logger.info("No loans found for merchantId: {}", merchantId);
            responseDTO.setLoans(Collections.emptyList());
            responseDTO.setMessage("No merchant loans found");
            responseDTO.setSuccess(false);
        } else {
            logger.info("{} loans found for merchantId: {}", merchantLoans.size(), merchantId);
            responseDTO.setLoansFromLendingPaymentSchedule(merchantLoans);
            for (LendingMerchantLoansResponseDTO.Loan loan : responseDTO.getLoans()) {
                LendingLedger lendingLedger = lendingLedgerDao.findLastPaymentEntryByMerchantAndLoan(merchantId, loan.getLoanId());
                if (lendingLedger != null) {
                    loan.setLastEdiPaid(lendingLedger.getAmount());
                } else {
                    loan.setLastEdiPaid(0D);
                }
                LendingEDISchedule lendingEDISchedule = lendingEDIScheduleDao.getLatestByLoanId(loan.getLoanId());
                if(lendingEDISchedule != null){
                    loan.setShowCustomAmount(true);
                }
            }
//            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId,"ACTIVE");
//            if(lendingPaymentSchedule != null){
//                try {
//                    List<LoanEligibilityDTO> loans = topupLoan(lendingPaymentSchedule);
//                    if (!loans.isEmpty()) {
//                        responseDTO.setEligibility(loans);
//                        responseDTO.setTopup(Boolean.TRUE);
//                    }
//                } catch (Exception e) {
//                    logger.error("Exception while calculating TOPUP loan for merchant:{}", merchantId, e);
//                }
//            }
            responseDTO.getLoans().sort(Comparator.comparing(LendingMerchantLoansResponseDTO.Loan::getLoanId, Comparator.reverseOrder()));
            responseDTO.setMessage("Successfully fetched merchant loans");
            responseDTO.setSuccess(true);
        }
        return responseDTO;
    }

    private List<LoanEligibilityDTO> topupLoan(LendingPaymentSchedule lendingPaymentSchedule){
        MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(lendingPaymentSchedule.getMerchant().getId());
        Experian experian = experianDao.getByMerchantId(lendingPaymentSchedule.getMerchant().getId());
        double tpv = (merchantSummary != null && merchantSummary.getTpv1Mon() != null) ? merchantSummary.getTpv1Mon() : 0d;

        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();

        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchant(lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getMerchant());
        if (lendingApplication == null || "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType())) {
            logger.info("Lending Application not found/topup loan for merchant:{}", lendingPaymentSchedule.getMerchant().getId());
            return eligiblity;
        }

        if(!"APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus())){
            logger.info("Lending Application Nach Not Done For this  merchant:{}", lendingApplication.getMerchant().getId());
            return eligiblity;
        }

        if(tpv/lendingApplication.getEdi() < 1.5){
            logger.info("Topup Loan Merchant TPV is not match For merchant:{}",  lendingPaymentSchedule.getMerchant().getId());
            return eligiblity;
        }

        double dpd = lendingPaymentSchedule.getDueAmount() / lendingPaymentSchedule.getEdiAmount();
        if(dpd > 5D) {
            logger.info("DPD is greater than 5 for merchant ID {}",  lendingPaymentSchedule.getMerchant().getId());
            return eligiblity;
        }

        BigInteger maxDpd = loanDpdDao.findMaxDpd(lendingPaymentSchedule.getId());
        if(maxDpd.intValue() > 10){
            logger.info("Merchant Dpd Greater than 10 merchant:{}",  lendingPaymentSchedule.getMerchant().getId());
            return eligiblity;
        }

        double paidRatio = 0d;
        if (lendingPaymentSchedule.getPaidPrinciple() != null && lendingPaymentSchedule.getLoanAmount() != null) {
            paidRatio = lendingPaymentSchedule.getPaidPrinciple() / lendingPaymentSchedule.getLoanAmount();
        }

        if(paidRatio < 0.75D || paidRatio >0.95D){
            logger.info("Insufficient paid ratio for merchant ID {}",  lendingPaymentSchedule.getMerchant().getId());
            return eligiblity;
        }

        Double eligibleAmount = 0D;
        GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(lendingPaymentSchedule.getMerchant().getId());
        if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
            logger.info("Global limit for merchant:{} is {}", lendingPaymentSchedule.getMerchant().getId(), globalLimitResponse.getData().getGlobalLimit());
            eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
        }

        if(eligibleAmount < lendingPaymentSchedule.getLoanAmount() * 1.5){
            logger.info("Eligible Loan Amount Is lessthan 1.5x for current Loan Amount for merchant ID {}",  lendingPaymentSchedule.getMerchant().getId());
            return eligiblity;
        }

        if(eligibleAmount > 300000D){
            eligibleAmount = 300000D;
        }

        List<LendingCategories> lendingCategories = lendingCategoryDao.getByMasterCategoryForConstruct1("TOPUP");
        LoanCalculationUtil.LoanBreakupDetail breakup;
        AvailableLoan availableLoan = new AvailableLoan();
        availableLoan.setAmount(eligibleAmount);

        for(LendingCategories category : lendingCategories){

            breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, category, "TOPUP");
            EligibleLoan eligibleLoan = new EligibleLoan();
            eligibleLoan.setMerchantId(lendingPaymentSchedule.getMerchant().getId());
            eligibleLoan.setExperianId(experian.getId());
            eligibleLoan.setTenure(category.getPayableConverter());
            eligibleLoan.setStatus("ACTIVE");
            eligibleLoan.setAmount(eligibleAmount);
            eligibleLoan.setCategory(category.getCategory());
            eligibleLoan.setEdi(breakup.getEdi());
            eligibleLoan.setIoEdi(breakup.getIoEdi());
            eligibleLoan.setRepayment(breakup.getRepayment());
            eligibleLoan.setLoanConstruct(breakup.getConstruct());
            eligibleLoan.setLoanType("TOPUP");
            eligibleLoanDao.save(eligibleLoan);

            double prevLoanUnpaidAmount = (lendingPaymentSchedule.getLoanAmount() - lendingPaymentSchedule.getPaidPrinciple()) + lendingPaymentSchedule.getDueInterest();
            LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
            loanEligibilityDTO.setPrevLoanUnpaidAmount((int) prevLoanUnpaidAmount);
            loanEligibilityDTO.setDisbursementAmount(breakup.getDisbursementAmount());
            loanEligibilityDTO.setProcessingFee(breakup.getProcessingFee());
            loanEligibilityDTO.setInterestRate(breakup.getEffectiveInterestRate());
            loanEligibilityDTO.setAmount(breakup.getLoanAmount());
            loanEligibilityDTO.setCategory(category.getCategory());
            loanEligibilityDTO.setInterestAmount(breakup.getTotalInterestAmount());
            loanEligibilityDTO.setEdi(breakup.getEdi());
            loanEligibilityDTO.setRepayment(breakup.getRepayment());
            loanEligibilityDTO.setTenure(eligibleLoan.getTenure());
            loanEligibilityDTO.setConstruct(breakup.getConstruct());
            loanEligibilityDTO.setList(LoanCalculationUtil.prepareLabels(breakup, breakup.getIoOrFreeEdiTenure()));
            loanEligibilityDTO.setType(breakup.getType());
            loanEligibilityDTO.setOptionEnable(true);
            loanEligibilityDTO.setPrincipleEdiTenure(breakup.getPrincipleEdiTenure());
            loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getDisbursementAmount() - (int) prevLoanUnpaidAmount);
            loanEligibilityDTO.setLoanType("TOPUP");
            loanEligibilityDTO.setEdiCount(category.getPayableDays());
            eligiblity.add(loanEligibilityDTO);
        }
        experian.setEligibleAmount(eligibleAmount);
        experian.setLoanType("TOPUP");
        experianDao.save(experian);

        int deleteNonTopup = eligibleLoanDao.deleteNonTopUp(lendingPaymentSchedule.getMerchant().getId());

        return eligiblity;
    }
}
