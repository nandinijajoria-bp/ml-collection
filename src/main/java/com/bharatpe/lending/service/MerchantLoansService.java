package com.bharatpe.lending.service;

import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.dao.LoanDpdDao;
import com.bharatpe.lending.common.entity.BpEnach;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.LoanPaymentOrder;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.*;

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

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    BPEnachDao bpEnachDao;

    @Autowired
    LendingRedCitiesDao lendingRedCitiesDao;

    @Autowired
    LoanPaymentOrderDao loanPaymentOrderDao;

    @Autowired
    MerchantDao merchantDao;

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
        responseDTO.setAccountDetails(loanUtil.getAccountDetails(merchantId));
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
                loan.setDpd(LoanUtil.calculateDPD(loan.getEdiAmount(),loan.getDueAmount()));
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
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId,"ACTIVE");
            if (lendingPaymentSchedule != null) {
                Date date = new Date();
                if(date.after(lendingPaymentSchedule.getStartDate())) {
                    responseDTO.setEdiStarted(Boolean.TRUE);
                } else {
                    responseDTO.setEdiStarted(Boolean.FALSE);
                }
                List<LendingMerchantLoansResponseDTO.RepaymentDetails> repaymentDetailsList = new ArrayList<>();
                List<LoanPaymentOrder> loanPaymentOrderList = loanPaymentOrderDao.findRecentTransactions(lendingPaymentSchedule.getId(),lendingPaymentSchedule.getMerchant().getId());
                if(loanPaymentOrderList != null) {
                    for (LoanPaymentOrder loanPaymentOrder : loanPaymentOrderList) {
                        LendingMerchantLoansResponseDTO.RepaymentDetails repaymentDetails = new LendingMerchantLoansResponseDTO.RepaymentDetails();
                        repaymentDetails.setAmount(loanPaymentOrder.getAmount());
                        repaymentDetails.setDate(loanPaymentOrder.getCreatedAt());
                        repaymentDetails.setMode(LoanUtil.settlementMode.getOrDefault(loanPaymentOrder.getSource(), "UPI"));
                        repaymentDetails.setStatus(loanPaymentOrder.getStatus());
                        repaymentDetails.setOrderId(loanPaymentOrder.getOrderId());
                        repaymentDetailsList.add(repaymentDetails);
                    }
                    responseDTO.setRepaymentDetails(repaymentDetailsList);
                }
                boolean pennyDrop = loanUtil.checkPennyDrop(lendingPaymentSchedule.getMerchant());
                if(pennyDrop){
                    try {
                        List<LoanEligibilityDTO> loans = topupLoan(lendingPaymentSchedule);
                        if (!loans.isEmpty()) {
                            responseDTO.setEligibility(loans);
                            responseDTO.setTopup(Boolean.TRUE);
                            responseDTO.setTopupLender(!Lender.LDC.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) ? Lender.LDC.name() : Lender.MAMTA.name());
                        }
                    } catch (Exception e) {
                        logger.error("Exception while calculating TOPUP loan for merchant:{}", merchantId, e);
                    }
                }
            }

            responseDTO.getLoans().sort(Comparator.comparing(LendingMerchantLoansResponseDTO.Loan::getLoanId, Comparator.reverseOrder()));
            responseDTO.setMessage("Successfully fetched merchant loans");
            responseDTO.setSuccess(true);
        }
        return responseDTO;
    }

    private LoanCalculationUtil.LoanBreakupDetail calculateHalfIOLoan(LendingPaymentSchedule lendingPaymentSchedule, Long merchantId, LoanType loanType) {
        try {
            double foreclosureAmount = (int) Math.ceil(lendingPaymentSchedule.getLoanAmount() - (lendingPaymentSchedule.getPaidPrinciple() != null ? lendingPaymentSchedule.getPaidPrinciple() : 0) + (lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0));
            double loanAmount = Math.ceil(foreclosureAmount / 1000.0) * 1000;
            if (loanAmount < 10000d || lendingPaymentSchedule.getEdiRemainingCount() < 26) {
                logger.info("loan amount less than 10k for merchant:{} and loanType:{}", merchantId, loanType.name());
                return null;
            }
            logger.info("Calculating " + loanType.name() + " for merchant:{} for amount:{}", merchantId, loanAmount);
            int newEdiCount = calculateNewTenure(lendingPaymentSchedule.getEdiRemainingCount(), loanType);
            if (newEdiCount == 0) {
                return null;
            }
            LendingCategories lendingCategory = lendingCategoryDao.getByMasterCategoryAndPayableDays(loanType.name(), newEdiCount);
            if (lendingCategory == null) {
                logger.error("Lending category not found for loanType:{} and days:{}", loanType.name(), newEdiCount);
                return null;
            }
            Experian experian = experianDao.getByMerchantId(merchantId);
            AvailableLoan availableLoan = new AvailableLoan();
            availableLoan.setAmount(loanAmount);
            LoanCalculationUtil.LoanBreakupDetail breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, lendingCategory, loanType.name());
            eligibleLoanDao.deleteByMerchantId(merchantId);
            eligibleLoanDao.deleteCustomOffers(merchantId);
            insertEligibleLoan(merchantId, experian, breakup, lendingCategory);
            return breakup;
        } catch (Exception e) {
            logger.error("Exception calculating half topup loan for merchant:{}", merchantId, e);
        }
        return null;
    }

    private void insertEligibleLoan(Long merchantId, Experian experian, LoanCalculationUtil.LoanBreakupDetail breakup, LendingCategories category) {
        try {
            EligibleLoan eligibleLoan = new EligibleLoan();
            eligibleLoan.setMerchantId(merchantId);
            eligibleLoan.setExperianId(experian != null ? experian.getId() : null);
            eligibleLoan.setTenure(category.getPayableConverter());
            eligibleLoan.setStatus("ACTIVE");
            eligibleLoan.setAmount(breakup.getLoanAmount().doubleValue());
            eligibleLoan.setCategory(category.getCategory());
            eligibleLoan.setEdi(breakup.getEdi());
            eligibleLoan.setIoEdi(breakup.getIoEdi());
            eligibleLoan.setRepayment(breakup.getRepayment());
            eligibleLoan.setLoanConstruct(breakup.getConstruct());
            eligibleLoan.setLoanType(category.getMasterCategory());
            eligibleLoan.setIoEdiDays(breakup.getIoEdiDays());
            eligibleLoanDao.save(eligibleLoan);
        } catch (Exception e) {
            logger.error("Exception while saving eligible loan for merchant:{}", merchantId, e);
        }
    }

    private int calculateNewTenure(Integer ediRemainingCount, LoanType loanType) {
        if (loanType.equals(LoanType.HALF_TOPUP)) {
            if (ediRemainingCount >= 26 && ediRemainingCount <= 38) {
                return 77;
            } else if (ediRemainingCount >= 39 && ediRemainingCount <= 77) {
                return 155;
            } else if (ediRemainingCount >= 78 && ediRemainingCount <= 117) {
                return 234;
            } else if (ediRemainingCount >= 118 && ediRemainingCount <= 155) {
                return 311;
            } else if (ediRemainingCount >= 156 && ediRemainingCount <= 234) {
                return 388;
            }
        } else if (loanType.equals(LoanType.IO_TOPUP)) {
            if (ediRemainingCount >= 26 && ediRemainingCount <= 76) {
                return 77;
            } else if (ediRemainingCount >= 77 && ediRemainingCount <= 154) {
                return 155;
            } else if (ediRemainingCount >= 155 && ediRemainingCount <= 233) {
                return 234;
            } else if (ediRemainingCount >= 234 && ediRemainingCount <= 310) {
                return 311;
            }
        }
        return 0;
    }

    private boolean baseChecksForHalfAndIOEdi(LendingPaymentSchedule lendingPaymentSchedule, LendingMerchantLoansResponseDTO responseDTO) {
        if(responseDTO.getTopup()){
            return false;
        }
        try {
            List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
            if (lendingPaymentSchedule.getLoanApplication() != null && topupLoans.contains(lendingPaymentSchedule.getLoanApplication().getLoanType())) {
                logger.info("Previous loan is topup for merchant:{}", lendingPaymentSchedule.getMerchant().getId());
                return false;
            }
            BpEnach bpEnach = bpEnachDao.findSuccessEnach(lendingPaymentSchedule.getMerchant().getId());
            if (bpEnach == null) {
                logger.info("Nach not success for merchant:{}", lendingPaymentSchedule.getMerchant().getId());
                return false;
            }
            if (LoanUtil.getDateDiffInDays(lendingPaymentSchedule.getCreatedAt(), new Date()) <= 30) {
                return false;
            }
            if (lendingPaymentSchedule.getLoanApplication() != null && lendingPaymentSchedule.getLoanApplication().getPincode() != null) {
                LendingRedCities redCities = lendingRedCitiesDao.findByPincode(lendingPaymentSchedule.getLoanApplication().getPincode().intValue());
                if (redCities != null) {
                    logger.info("Red pincode for merchant:{}", lendingPaymentSchedule.getMerchant().getId());
                    return false;
                }
            }
            double paidRatio = lendingPaymentSchedule.getPaidPrinciple() != null ? (lendingPaymentSchedule.getPaidPrinciple() / lendingPaymentSchedule.getLoanAmount()) : 0d;
            double dpd = lendingPaymentSchedule.getDueAmount() / lendingPaymentSchedule.getEdiAmount();
            double foreclosureAmount = (int) Math.ceil(lendingPaymentSchedule.getLoanAmount() - (lendingPaymentSchedule.getPaidPrinciple() != null ? lendingPaymentSchedule.getPaidPrinciple() : 0) + (lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0));
            foreclosureAmount = Math.ceil(foreclosureAmount / 1000.0) * 1000;
            return lendingPaymentSchedule.getEdiRemainingCount() >= 26 && paidRatio < 0.75D && foreclosureAmount >= 10000d && dpd >= 10d && dpd <= 45d;
        } catch (Exception e) {
            logger.error("Exception in half io loans base checks for loanId:{}", lendingPaymentSchedule.getId(), e);
        }
        return false;
    }

    private List<LoanEligibilityDTO> topupLoan(LendingPaymentSchedule lendingPaymentSchedule){
        MerchantSummary merchantSummary = merchantSummaryDao.getByMerchantId(lendingPaymentSchedule.getMerchant().getId());
        Experian experian = experianDao.getByMerchantId(lendingPaymentSchedule.getMerchant().getId());
        double tpv = (merchantSummary != null && merchantSummary.getTpv1Mon() != null) ? merchantSummary.getTpv1Mon() : 0d;

        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();

        List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
        LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchant(lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getMerchant());
        if (lendingApplication == null || topupLoans.contains(lendingApplication.getLoanType())) {
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
        if(dpd > 2D) {
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

    public CommonResponse getDueAmount(Long merchantId, Long merchantStoreId, Merchant merchant) {
        if (merchant == null) {
            merchant = merchantDao.getById(merchantId);
        }
        if (merchant == null) {
            return new CommonResponse(false, "merchant does not exist");
        }
        Map<String, Double> responseMap = new HashMap<>();
        Double dueAmount = 0D;
        List<LendingPaymentSchedule> activeLoans = fetchLendingPaymentSchedule(merchant.getId(), merchantStoreId, "ACTIVE");
        if (!activeLoans.isEmpty() && "BHARATPE_ACCOUNT".equalsIgnoreCase(merchant.getSettlementType())) {
            for (LendingPaymentSchedule activeLoan : activeLoans) {
                dueAmount += activeLoan.getDueAmount();
            }
        }
        responseMap.put("due_amount", dueAmount);
        return new CommonResponse(responseMap);
    }
}
