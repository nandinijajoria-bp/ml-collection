package com.bharatpe.lending.service;

import com.bharatpe.cache.DTO.AddCacheDto;
import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Handler.PhonebookHandler;
import com.bharatpe.lending.common.dto.PhonebookDTO;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.query.entity.LendingPaymentScheduleSlave;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.LendingContactSyncAudit;
import com.bharatpe.lending.common.entity.LendingIoHalfTopup;
import com.bharatpe.lending.common.entity.LendingPrepayment;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.entity.LoanPaymentOrder;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.query.dao.LendingPaymentScheduleDaoSlave;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

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

//    @Autowired
//    MerchantSummaryDao merchantSummaryDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LendingEDIScheduleDao lendingEDIScheduleDao;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LoanPaymentOrderDao loanPaymentOrderDao;

    @Autowired
    LendingPrepaymentDao lendingPrepaymentDao;

//    @Autowired
//    MerchantDao merchantDao;

    @Autowired
    LendingIoHalfTopupDao lendingIoHalfTopupDao;

    @Autowired
    PhonebookHandler phonebookHandler;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    LendingContactSyncAuditDao lendingContactSyncAuditDao;

    @Value("${topup.loan.enabled:false}")
    Boolean isTopUpEnabled;

    @Autowired
    LendingCache lendingCache;

    @Value("${due.amount.caching.window:60}")
    int dueAmountCachingWindow;

    @Autowired
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @Autowired
    LendingPaymentScheduleDaoSlave lendingPaymentScheduleDaoSlave;

    @Autowired
    MerchantService merchantService;

    private final DecimalFormat df = new DecimalFormat("#.##");

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

    private List<LendingPaymentScheduleSlave> fetchLendingPaymentScheduleSlave(Long merchantId, Long merchantStoreId, String status) {
        if (merchantStoreId != null) {
            return lendingPaymentScheduleDaoSlave.findByMerchantIdAndMerchantStoreIdAndStatus(merchantId, merchantStoreId,
              status);
        }
        return lendingPaymentScheduleDaoSlave.findByMerchantIdAndStatusList(merchantId, status);
    }

    public LendingPaymentScheduleStatusDTO firstLoanStatus(Long merchantId, Long merchantStoreId) {
        final LendingPaymentScheduleSlave loan =
          lendingPaymentScheduleDaoSlave.findTop1ByMerchantIdAndMerchantStoreId(merchantId, merchantStoreId);

        if (Objects.nonNull(loan))
            return LendingPaymentScheduleStatusDTO.builder().id(loan.getId()).status(loan.getStatus()).build();

        return null;
    }

    public LendingPaymentScheduleStatusDTO checkLoanStatus(Long merchantId, Long merchantStoreId) {
        LendingPaymentScheduleSlave loan;
        if (merchantStoreId != null) {
            loan = lendingPaymentScheduleDaoSlave.findTop1ByMerchantIdAndMerchantStoreId(merchantId, merchantStoreId);
        } else {
            loan = lendingPaymentScheduleDaoSlave.findTop1ByMerchantId(merchantId);
        }

        if (Objects.nonNull(loan))
            return LendingPaymentScheduleStatusDTO.builder().id(loan.getId()).status(loan.getStatus()).build();

        return null;
    }

    public List<LoansHistoryResponseDTO> getLoansHistory(Long merchantId, Long merchantStoreId) {
        final List<LendingPaymentScheduleSlave> loans =
          lendingPaymentScheduleDaoSlave.findByMerchantIdAndMerchantStoreId(merchantId, merchantStoreId);

        List<LoansHistoryResponseDTO> loansHistory = new ArrayList<>();

        for (LendingPaymentScheduleSlave loan : loans) {
            loansHistory.add(createLoanHistoryDTO(loan));
        }

        return loansHistory;
    }

    public LoanDetailsAndStatementDTO getLoanDetailsAndStatement(Long merchantId, Long merchantStoreId) {
        return LoanDetailsAndStatementDTO.builder()
          .loanDetails(getActiveLoanDetails(merchantId, merchantStoreId))
          .loanStatement(getLoanStatement(merchantId, merchantStoreId)).build();
    }

    private LendingPaymentScheduleDetailsDTO getActiveLoanDetails(Long merchantId, Long merchantStoreId) {
        final LendingPaymentScheduleSlave activeLoan = lendingPaymentScheduleDaoSlave.findTop1ByMerchantIdAndMerchantStoreIdAndStatusOrderByIdDesc(merchantId,
          merchantStoreId, "ACTIVE");

        if (Objects.nonNull(activeLoan))
            return LendingPaymentScheduleDetailsDTO.builder()
              .loanId(activeLoan.getId())
              .loanAmount(activeLoan.getLoanAmount())
              .paidAmount(activeLoan.getPaidAmount())
              .totalPayableAmount(activeLoan.getTotalPayableAmount())
              .ediAmount(activeLoan.getEdiAmount())
              .ediCount(activeLoan.getEdiCount())
              .build();

        return null;
    }

    private List<LendingPaymentScheduleDaoSlave.LoanStatementDTO> getLoanStatement(Long merchantId, Long merchantStoreId) {
        return lendingPaymentScheduleDaoSlave.getLoanStatement(merchantId,
          merchantStoreId);
    }

    private LoansHistoryResponseDTO createLoanHistoryDTO(LendingPaymentScheduleSlave lendingPaymentScheduleSlave) {
        Float tenure = getTenure(lendingPaymentScheduleSlave.getEdiCount());
        Double interestRate =  calculateInterestRate(lendingPaymentScheduleSlave, tenure);

        return LoansHistoryResponseDTO.builder()
          .loanId(lendingPaymentScheduleSlave.getId())
          .loanAmount(lendingPaymentScheduleSlave.getLoanAmount())
          .status(lendingPaymentScheduleSlave.getStatus())
          .startDate(lendingPaymentScheduleSlave.getStartDate())
          .closingDate(lendingPaymentScheduleSlave.getClosingDate())
          .tentativeClosingDate(lendingPaymentScheduleSlave.getTentativeClosingDate())
          .interestRate(interestRate)
          .tenure(tenure)
          .build();
    }

    private Float getTenure(Integer ediCount) {
        final LendingCategories lendingCategory = lendingCategoryDao.findTop1ByPayableDays(ediCount);
        if (Objects.nonNull(lendingCategory))
            return lendingCategory.getTenureMonths();

        return null;
    }

    private Double calculateInterestRate(LendingPaymentScheduleSlave lendingPaymentScheduleSlave, Float tenure) {
        Double interestRate = null;
        if (Objects.nonNull(tenure) & tenure > 0) {
            interestRate = (((lendingPaymentScheduleSlave.getTotalPayableAmount()/lendingPaymentScheduleSlave.getLoanAmount()) - 1) / tenure) * 100;
            interestRate = Double.valueOf(df.format(interestRate));
        }
        return interestRate;
    }


    public LendingMerchantLoansResponseDTO getMerchantLoans(Long merchantId) {
        LendingMerchantLoansResponseDTO responseDTO = new LendingMerchantLoansResponseDTO();
        responseDTO.setTopup(Boolean.FALSE);
        List<LendingPaymentSchedule> merchantLoans = lendingPaymentScheduleDao.findByMerchantIdAndCreditLoan(merchantId, false);
        responseDTO.setAccountDetails(loanUtil.getAccountDetails(merchantId));


        //refresh eligibility after top up loan created_at check.
        EligibleLoan topupLoan = eligibleLoanDao.findTop1ByMerchantIdAndLoanTypeOrderByIdDesc(merchantId, "TOPUP");
        LendingLedger ledger = lendingLedgerDao.findLastLedgerEntry(merchantId, topupLoan.getCreatedAt());
        if(!ObjectUtils.isEmpty(ledger)){
            apiGatewayService.getGlobalLimit(merchantId, null, 318, null);
        }

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
                loan.setDpd(LoanUtil.calculateDPD(loan.getEdiAmount(), loan.getDueAmount()));
                if (lendingLedger != null) {
                    loan.setLastEdiPaid(lendingLedger.getAmount());
                } else {
                    loan.setLastEdiPaid(0D);
                }
                LendingEDISchedule lendingEDISchedule = lendingEDIScheduleDao.getLatestByLoanId(loan.getLoanId());
                if (lendingEDISchedule != null) {
                    loan.setShowCustomAmount(true);
                }
                LendingPrepayment lendingPrepayment = lendingPrepaymentDao.findByMerchantIdAndLoanId(merchantId, loan.getLoanId());
                double advanceEdiAmount = lendingPrepayment != null && lendingPrepayment.getAdvanceEdiAmount() != null ? lendingPrepayment.getAdvanceEdiAmount() : 0d;
                loan.setPaidAmount((ObjectUtils.isEmpty(loan.getPaidAmount()) ? 0 : loan.getPaidAmount()) + advanceEdiAmount);
                loan.setPendingAmount((ObjectUtils.isEmpty(loan.getPendingAmount()) ? 0 : loan.getPendingAmount()) - advanceEdiAmount);
                loan.setPaidPrinciple((ObjectUtils.isEmpty(loan.getPaidPrinciple()) ? 0 : loan.getPaidPrinciple()) + advanceEdiAmount);
            }
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId, "ACTIVE");
            if (lendingPaymentSchedule != null) {
                Date date = new Date();
                if (date.after(lendingPaymentSchedule.getStartDate())) {
                    responseDTO.setEdiStarted(Boolean.TRUE);
                } else {
                    responseDTO.setEdiStarted(Boolean.FALSE);
                }
                List<LendingMerchantLoansResponseDTO.RepaymentDetails> repaymentDetailsList = new ArrayList<>();
                List<LoanPaymentOrder> loanPaymentOrderList = loanPaymentOrderDao.findRecentTransactions(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getMerchantId());
                if (loanPaymentOrderList != null) {
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
                boolean pennyDrop = loanUtil.checkPennyDrop(lendingPaymentSchedule.getMerchantId());
                if (pennyDrop) {
                    try {
                        List<LoanEligibilityDTO> loans = topupLoan(lendingPaymentSchedule);
                        if (!loans.isEmpty()) {
                            responseDTO.setEligibility(loans);
                            responseDTO.setTopup(Boolean.TRUE);
//                            responseDTO.setTopupLender(!Lender.LDC.name().equalsIgnoreCase(lendingPaymentSchedule.getNbfc()) ? Lender.LDC.name() : Lender.MAMTA.name());
                            responseDTO.setTopupLender(Lender.LDC.name());
                        }
                    } catch (Exception e) {
                        logger.error("Exception while calculating TOPUP loan for merchant:{}", merchantId, e);
                    }
                    if (baseChecksForHalfAndIOEdi(lendingPaymentSchedule, responseDTO)) {
                        logger.info("Base checks passed for Half/IO Loan for loanId:{}", lendingPaymentSchedule.getId());
                        LendingIoHalfTopup lendingIoHalfTopup = lendingIoHalfTopupDao.findByLoanId(lendingPaymentSchedule.getId());
                        LoanCalculationUtil.LoanBreakupDetail loanBreakupDetail;
                        if (lendingIoHalfTopup != null && LoanType.IO_TOPUP.name().equals(lendingIoHalfTopup.getLoanType())) {
                            logger.info("merchant:{} eligible for io loan", merchantId);
                            loanBreakupDetail = calculateHalfIOLoan(lendingPaymentSchedule, merchantId, LoanType.IO_TOPUP);
                            responseDTO.setIoLoan(lendingPaymentSchedule, loanBreakupDetail);
                        } else if (lendingIoHalfTopup != null && LoanType.HALF_TOPUP.name().equals(lendingIoHalfTopup.getLoanType())) {
                            logger.info("merchant:{} eligible for half loan", merchantId);
                            loanBreakupDetail = calculateHalfIOLoan(lendingPaymentSchedule, merchantId, LoanType.HALF_TOPUP);
                            responseDTO.setHalfLoan(lendingPaymentSchedule, loanBreakupDetail);
                        } else {
                            logger.info("Entry not found in lending_io_half_topup for merchant:{}", merchantId);
                        }
                    }
                }
                responseDTO.setContactSync(isContactSyncRequired(lendingPaymentSchedule));
            }

            responseDTO.getLoans().sort(Comparator.comparing(LendingMerchantLoansResponseDTO.Loan::getLoanId, Comparator.reverseOrder()));
            responseDTO.setMessage("Successfully fetched merchant loans");
            responseDTO.setSuccess(true);
        }
        return responseDTO;
    }

    private Boolean isContactSyncRequired(LendingPaymentSchedule lendingPaymentSchedule) {
        try {
            LendingContactSyncAudit lendingContactSyncAudit = lendingContactSyncAuditDao.findTop1ByMerchantId(lendingPaymentSchedule.getMerchantId());
            if (Objects.nonNull(lendingContactSyncAudit) &&
              lendingContactSyncAudit.getTotalEntries() >= 100 &&
              (float) lendingContactSyncAudit.getNameEntries() / lendingContactSyncAudit.getTotalEntries() >= 0.25 &&
              (float) lendingContactSyncAudit.getMobileEntries() / lendingContactSyncAudit.getTotalEntries() >= 0.25
            ) {
                return false;
            }

            List<PhonebookDTO> phonebook = phonebookHandler.getPhonebook(lendingPaymentSchedule.getMerchantId());
            if (phonebook.isEmpty()) {
                return true;
            }
//            if (LoanUtil.getDateDiffInDays(phonebook.get().getUpdatedAt(), new Date()) > 60) {
//                return true;
//            }
            if (Objects.isNull(lendingContactSyncAudit)) {
                lendingContactSyncAudit = new LendingContactSyncAudit();
                lendingContactSyncAudit.setMerchantId(lendingPaymentSchedule.getMerchantId());
            }
//            String[] s3Url = phonebook.get().getS3URL().split("/");
//            String fileName = s3Url[s3Url.length - 1];
//            logger.info("Filename for loanId: {}, {}", lendingPaymentSchedule.getId(), fileName);
            Long totalEntries = 0l, nameEntries = 0l, mobileEntries = 0l;
            try {
//                InputStream inputStream = s3BucketHandler.getObject(fileName, "merchant-phonebook", "us-west-2");
//                if (ObjectUtils.isEmpty(inputStream)) {
//                    return true;
//                }
//                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
//                String readLine = bufferedReader.readLine();
//                readLine = bufferedReader.readLine();

//                while (Objects.nonNull(readLine)) {
//                    totalEntries++;
//                    logger.info("phonebook for loan id : {}, readline: {}", lendingPaymentSchedule.getId(), readLine);
//                    String[] arr = readLine.split(",");
//                    String name = "";
//                    if (arr.length >= 1) {
//                        name = arr[0];
//                    }
//                    String mobile = "";
//                    if (arr.length >= 2) {
//                        mobile = arr[1];
//                    }
//                    if (!StringUtils.isEmpty(name)) {
//                        nameEntries++;
//                    }
//                    if (!StringUtils.isEmpty(mobile)) {
//                        mobileEntries++;
//                    }
//                    readLine = bufferedReader.readLine();
//                }

                for (PhonebookDTO phonebookDTO : phonebook) {
                    totalEntries++;
                    if (!StringUtils.isEmpty(phonebookDTO.getName())) {
                        nameEntries++;
                    }
                    if (!StringUtils.isEmpty(phonebookDTO.getPhoneNumber())) {
                        mobileEntries++;
                    }
                }


                lendingContactSyncAudit.setMobileEntries(mobileEntries);
                lendingContactSyncAudit.setNameEntries(nameEntries);
                lendingContactSyncAudit.setTotalEntries(totalEntries);
                lendingContactSyncAuditDao.save(lendingContactSyncAudit);
            } catch (Exception ex) {
                logger.error("Error Occured while auditing contact data for loan id : {} {}", lendingPaymentSchedule.getId(), ex);
            }
            if (totalEntries < 100 || (float) nameEntries / totalEntries < 0.25 || (float) mobileEntries / totalEntries < 0.25) {
                return true;
            }
        } catch (Exception ex) {
            logger.error("Exception Occured while checking contact sync required for loan id : {}, {}", lendingPaymentSchedule.getId(), ex);
        }
        return false;
    }

    private LoanCalculationUtil.LoanBreakupDetail calculateHalfIOLoan(LendingPaymentSchedule lendingPaymentSchedule, Long merchantId, LoanType loanType) {
        try {
            int foreclosureAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
            int processingFee = loanUtil.getIoHalfPF(lendingPaymentSchedule);
            double loanAmount = Math.ceil((foreclosureAmount + processingFee) / 1000.0) * 1000;
            int ediPaidCount = (int) Math.ceil(lendingPaymentSchedule.getPaidAmount() / lendingPaymentSchedule.getEdiAmount());
            int ediRemainingCount = lendingPaymentSchedule.getEdiCount() - ediPaidCount;
            if (loanAmount < 10000d || ediRemainingCount < 26) {
                logger.info("loan amount less than 10k for merchant:{} and loanType:{}", merchantId, loanType.name());
                return null;
            }
            logger.info("Calculating " + loanType.name() + " for merchant:{} for amount:{}", merchantId, loanAmount);
            int newEdiCount = calculateNewTenure(ediRemainingCount, loanType);
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
            breakup.setProcessingFee(processingFee);
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
        if (responseDTO.getTopup()) {
            return false;
        }
        if (lendingPaymentSchedule.getMerchantId().equals(9319451L) || lendingPaymentSchedule.getMerchantId().equals(5352114L))
            return true;
        try {
            List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
            if (lendingPaymentSchedule.getLoanApplication() != null && topupLoans.contains(lendingPaymentSchedule.getLoanApplication().getLoanType())) {
                logger.info("Previous loan is topup for merchant:{}", lendingPaymentSchedule.getMerchantId());
                return false;
            }
            if (!loanUtil.isEnachDone(lendingPaymentSchedule.getMerchantId())) {
                logger.info("Nach not success for merchant:{}", lendingPaymentSchedule.getMerchantId());
                return false;
            }
            if (LoanUtil.getDateDiffInDays(lendingPaymentSchedule.getCreatedAt(), new Date()) <= 30) {
                return false;
            }
            int ediPaidCount = (int) Math.ceil(lendingPaymentSchedule.getPaidAmount() / lendingPaymentSchedule.getEdiAmount());
            int ediRemainingCount = lendingPaymentSchedule.getEdiCount() - ediPaidCount;
            double foreclosureAmount = (int) Math.ceil(lendingPaymentSchedule.getLoanAmount() - (lendingPaymentSchedule.getPaidPrinciple() != null ? lendingPaymentSchedule.getPaidPrinciple() : 0) + (lendingPaymentSchedule.getDueInterest() != null ? lendingPaymentSchedule.getDueInterest() : 0));
            foreclosureAmount = Math.ceil(foreclosureAmount / 1000.0) * 1000;
            return foreclosureAmount >= 10000d && ediRemainingCount >= 26;
        } catch (Exception e) {
            logger.error("Exception in half io loans base checks for loanId:{}", lendingPaymentSchedule.getId(), e);
        }
        return false;
    }

    public boolean excludeTopUpBaseChecks(Long merchantId) {
        return loanUtil.isInternalMerchant(merchantId);
    }

    public List<LoanEligibilityDTO> topupLoan(LendingPaymentSchedule lendingPaymentSchedule) {

        List<LoanEligibilityDTO> eligiblity = new ArrayList<>();
        LendingApplication lendingApplication =
          lendingApplicationDao.findByIdAndMerchantId(lendingPaymentSchedule.getApplicationId(), lendingPaymentSchedule.getMerchantId());
        try {
            if (!isTopUpEnabled) {
                logger.info("Topup are loans are disabled");
                return eligiblity;
            }
            if(!Lender.LDC.name().equals(lendingPaymentSchedule.getNbfc())){
                logger.info("Topup not enabled on lender:{}",lendingPaymentSchedule.getNbfc());
                return eligiblity;
            }
            if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {
                if (lendingApplication == null) {
                    logger.info("Lending Application not found/topup loan for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                if (LoanType.SMALL_TICKET.name().equals(lendingApplication.getLoanType())) {
                    logger.info("last loan is small ticket for merchant:{} with applicationId: {}", lendingPaymentSchedule.getMerchantId(), lendingApplication.getId());
                    return eligiblity;
                }
                if (!loanUtil.isEnachDone(lendingPaymentSchedule.getMerchantId())) {
                    logger.info("Nach not success for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                double dpd = lendingPaymentSchedule.getDueAmount() / lendingPaymentSchedule.getEdiAmount();
                if (dpd > 3D) {
                    logger.info("DPD is greater than 3 for merchant ID {}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                BigInteger maxDpd = loanDpdDao.findMaxDpd(lendingPaymentSchedule.getId());
                if (maxDpd.intValue() > 10) {
                    logger.info("Merchant Dpd Greater than 10 merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                double paidRatio = 0d;
                if (lendingPaymentSchedule.getPaidPrinciple() != null && lendingPaymentSchedule.getLoanAmount() != null) {
                    paidRatio = lendingPaymentSchedule.getPaidPrinciple() / lendingPaymentSchedule.getLoanAmount();
                }

                if (paidRatio < 0.6D || paidRatio > 0.95D) {
                    logger.info("Insufficient paid ratio for merchant ID {}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }

                Double settlementAmount = lendingLedgerDao.findSettlementAmount(lendingPaymentSchedule.getId());
                double qrPaidRatio = (settlementAmount / lendingPaymentSchedule.getPaidAmount()) * 100;
                if (qrPaidRatio < 70) {
                    logger.info("QR payment less than 70% for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
            }
            Integer ediPaidCount = lendingLedgerDao.findLedgerCountOnAmountGreaterThanEdiAmount(lendingPaymentSchedule.getId(), lendingPaymentSchedule.getEdiAmount());
            int paidCount = lendingPaymentSchedule.getEdiCount() - lendingPaymentSchedule.getEdiRemainingCount();
            logger.info("ediPaidCount:{} and paidCount:{} for merchant:{}", ediPaidCount, paidCount, lendingPaymentSchedule.getMerchantId());
            double ediPaidRatio = (ediPaidCount * 1.0 / paidCount) * 100;

            //Double eligibleAmount = 0D;
//            GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(lendingPaymentSchedule.getMerchantId());
//            if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
//                logger.info("Global limit for merchant:{} is {}", lendingPaymentSchedule.getMerchantId(), globalLimitResponse.getData().getGlobalLimit());
//                eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
//            }
//            if (eligibleAmount.equals(0D)) {
//                logger.info("No topup eligibility found for merchant:{}", lendingPaymentSchedule.getMerchantId());
//                return eligiblity;
//            }
//            if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {
//                if (ediPaidRatio < 65D) {
//                    logger.info("EDI paid ratio:{} is less than 65% for merchant:{}", ediPaidRatio, lendingPaymentSchedule.getMerchantId());
//                    eligibleAmount = Math.min(eligibleAmount, lendingPaymentSchedule.getLoanAmount());
//                }
//                int posAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
//                if (eligibleAmount - posAmount < 10000) {
//                    logger.info("Outstanding amount less than 10k for merchant:{}", lendingPaymentSchedule.getMerchantId());
//                    return eligiblity;
//                }
//            }

            List<EligibleLoan> eligibleLoanList = eligibleLoanDao.findByMerchantIdAndLoanType(lendingPaymentSchedule.getMerchantId(), "TOPUP");
            if (ObjectUtils.isEmpty(eligibleLoanList)) {
                Double eligibleAmount = 0D;
                GlobalLimitResponse globalLimitResponse = apiGatewayService.getGlobalLimit(lendingPaymentSchedule.getMerchantId());
                if (globalLimitResponse != null && globalLimitResponse.getData() != null && globalLimitResponse.getData().getGlobalLimit() != null) {
                    logger.info("Global limit for merchant:{} is {}", lendingPaymentSchedule.getMerchantId(), globalLimitResponse.getData().getGlobalLimit());
                    eligibleAmount = globalLimitResponse.getData().getGlobalLimit();
                }
                if (eligibleAmount.equals(0D)) {
                    logger.info("No topup eligibility found for merchant:{}", lendingPaymentSchedule.getMerchantId());
                    return eligiblity;
                }
                if (!excludeTopUpBaseChecks(lendingPaymentSchedule.getMerchantId())) {
                    if (ediPaidRatio < 60D) {
                        logger.info("EDI paid ratio:{} is less than 60% for merchant:{}", ediPaidRatio, lendingPaymentSchedule.getMerchantId());
                        eligibleAmount = Math.min(eligibleAmount, lendingPaymentSchedule.getLoanAmount());
                    }
                    int posAmount = loanUtil.getForeclosureAmount(lendingPaymentSchedule);
                    if (eligibleAmount - posAmount < 10000) {
                        logger.info("Outstanding amount less than 10k for merchant:{}", lendingPaymentSchedule.getMerchantId());
                        return eligiblity;
                    }
                }
                loanDetailsServiceV2.recomputeEligibleLoan(globalLimitResponse, eligibleAmount, lendingPaymentSchedule.getMerchantId());
                eligibleLoanList = eligibleLoanDao.findByMerchantIdAndLoanType(lendingPaymentSchedule.getMerchantId(), "TOPUP");
                Experian experian = experianDao.getByMerchantId(lendingPaymentSchedule.getMerchantId());
                experian.setEligibleAmount(eligibleAmount);
                experian.setLoanType("TOPUP");
                experianDao.save(experian);
            }
            double prevLoanUnpaidAmount = (lendingPaymentSchedule.getLoanAmount() - lendingPaymentSchedule.getPaidPrinciple()) + lendingPaymentSchedule.getDueInterest();
            if (!eligibleLoanList.isEmpty()) {
                Collections.sort(eligibleLoanList, (o1, o2) -> o1.getTenureInMonths() - o2.getTenureInMonths());
                EligibleLoan eligibleLoan = eligibleLoanList.get(0);
                logger.info("eligible loan: {}", eligibleLoan);
                LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
                loanEligibilityDTO.setActiveApplicationId(lendingPaymentSchedule.getId());
                loanEligibilityDTO.setPrevLoanUnpaidAmount((int) prevLoanUnpaidAmount);
                loanEligibilityDTO.setProcessingFee(eligibleLoan.getProcessingFee());
                loanEligibilityDTO.setInterestRate(eligibleLoan.getRateOfInterest());
                loanEligibilityDTO.setAmount(eligibleLoan.getAmount().intValue());
                loanEligibilityDTO.setCategory(eligibleLoan.getCategory());
                loanEligibilityDTO.setDisbursementAmount(eligibleLoan.getAmount().intValue() - eligibleLoan.getProcessingFee());
                loanEligibilityDTO.setEdi(eligibleLoan.getEdi());
                loanEligibilityDTO.setRepayment(eligibleLoan.getRepayment());
                loanEligibilityDTO.setTenure(eligibleLoan.getTenure());
                loanEligibilityDTO.setConstruct(eligibleLoan.getLoanConstruct());
                loanEligibilityDTO.setOptionEnable(true);
                loanEligibilityDTO.setInterestAmount(eligibleLoan.getRepayment() - eligibleLoan.getAmount().intValue());
                loanEligibilityDTO.setIoEdiCount(eligibleLoan.getIoEdiDays());
                loanEligibilityDTO.setIoEdi(eligibleLoan.getIoEdi());
                loanEligibilityDTO.setTenureInMonths(eligibleLoan.getTenureInMonths());
//              loanEligibilityDTO.setList(LoanCalculationUtil.prepareLabels(breakup, breakup.getIoOrFreeEdiTenure()));
//              loanEligibilityDTO.setType();
                loanEligibilityDTO.setPrincipleEdiTenure(eligibleLoan.getTenureInMonths());
                loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getDisbursementAmount() - (int) prevLoanUnpaidAmount);
                loanEligibilityDTO.setLoanType("TOPUP");
                loanEligibilityDTO.setEdiCount(eligibleLoan.getEdiCount());
                loanEligibilityDTO.setId(eligibleLoan.getId());
                eligiblity.add(loanEligibilityDTO);
            }

//            List<LendingCategories> lendingCategories = lendingCategoryDao.getByMasterCategoryForConstruct1("TOPUP");
//            LoanCalculationUtil.LoanBreakupDetail breakup;
//            AvailableLoan availableLoan = new AvailableLoan();
//            availableLoan.setAmount(eligibleAmount);

//            for (LendingCategories category : lendingCategories) {

//                breakup = LoanCalculationUtil.getLoanBreakup(availableLoan, category, "TOPUP");
//                EligibleLoan eligibleLoan = new EligibleLoan();
//                eligibleLoan.setMerchantId(lendingPaymentSchedule.getMerchantId());
//                eligibleLoan.setExperianId(experian.getId());
//                eligibleLoan.setTenure(category.getPayableConverter());
//                eligibleLoan.setStatus("ACTIVE");
//                eligibleLoan.setAmount(eligibleAmount);
//                eligibleLoan.setCategory(category.getCategory());
//                eligibleLoan.setEdi(breakup.getEdi());
//                eligibleLoan.setIoEdi(breakup.getIoEdi());
//                eligibleLoan.setRepayment(breakup.getRepayment());
//                eligibleLoan.setLoanConstruct(breakup.getConstruct());
//                eligibleLoan.setLoanType("TOPUP");
//                eligibleLoanDao.save(eligibleLoan);

//                double prevLoanUnpaidAmount = (lendingPaymentSchedule.getLoanAmount() - lendingPaymentSchedule.getPaidPrinciple()) + lendingPaymentSchedule.getDueInterest();
//                LoanEligibilityDTO loanEligibilityDTO = new LoanEligibilityDTO();
//                loanEligibilityDTO.setPrevLoanUnpaidAmount((int) prevLoanUnpaidAmount);
//                loanEligibilityDTO.setDisbursementAmount(breakup.getDisbursementAmount());
//                loanEligibilityDTO.setProcessingFee(breakup.getProcessingFee());
//                loanEligibilityDTO.setInterestRate(breakup.getEffectiveInterestRate());
//                loanEligibilityDTO.setAmount(breakup.getLoanAmount());
//                loanEligibilityDTO.setCategory(category.getCategory());
//                loanEligibilityDTO.setInterestAmount(breakup.getTotalInterestAmount());
//                loanEligibilityDTO.setEdi(breakup.getEdi());
//                loanEligibilityDTO.setRepayment(breakup.getRepayment());
//                loanEligibilityDTO.setTenure(eligibleLoan.getTenure());
//                loanEligibilityDTO.setConstruct(breakup.getConstruct());
//                loanEligibilityDTO.setList(LoanCalculationUtil.prepareLabels(breakup, breakup.getIoOrFreeEdiTenure()));
//                loanEligibilityDTO.setType(breakup.getType());
//                loanEligibilityDTO.setOptionEnable(true);
//                loanEligibilityDTO.setPrincipleEdiTenure(breakup.getPrincipleEdiTenure());
//                loanEligibilityDTO.setDisbursementAmount(loanEligibilityDTO.getDisbursementAmount() - (int) prevLoanUnpaidAmount);
//                loanEligibilityDTO.setLoanType("TOPUP");
//                loanEligibilityDTO.setEdiCount(category.getPayableDays());
//                eligiblity.add(loanEligibilityDTO);
//            }


//            int deleteNonTopup = eligibleLoanDao.deleteNonTopUp(lendingPaymentSchedule.getMerchantId());
        } catch (Exception ex) {
            logger.info("Exception Occured while checking eligibilty for topup");
        }
        return eligiblity;
    }

    public CommonResponse getDueAmount(Long merchantId, Long merchantStoreId, BasicDetailsDto basicDetailsDto) {
        if (basicDetailsDto == null) {
            final Optional<BasicDetailsDto> basicDetailsDtoOptional = merchantService.fetchMerchantBasicDetails(merchantId);
            if (basicDetailsDtoOptional.isPresent())
                basicDetailsDto = basicDetailsDtoOptional.get();
        }
        if (basicDetailsDto == null) {
            return new CommonResponse(false, "merchant does not exist");
        }
        Map<String, Double> responseMap = new HashMap<>();
        String dueAmountCacheKey = "DUE_AMT_" + basicDetailsDto.getId() + (ObjectUtils.isEmpty(merchantStoreId) ? "" : ("_" + merchantStoreId));
        try {
            Object dueAmountCached = lendingCache.get(dueAmountCacheKey);
            if (!ObjectUtils.isEmpty(dueAmountCached)) {
                responseMap.put("due_amount", (Double) dueAmountCached);
                return new CommonResponse(responseMap);
            }
        } catch (Exception e) {
            logger.error("exception occurred while retrieving data from redis for: {} {}", basicDetailsDto.getId(), e.getMessage());
        }
        Double dueAmount = 0D;
        List<LendingPaymentScheduleSlave> activeLoans = fetchLendingPaymentScheduleSlave(basicDetailsDto.getId(), merchantStoreId, "ACTIVE");
        if (!activeLoans.isEmpty()) {
            for (LendingPaymentScheduleSlave activeLoan : activeLoans) {
                dueAmount += activeLoan.getDueAmount();
            }
        }
        Double creditCardDueAmount = apiGatewayService.getCreditCardDueAmount(basicDetailsDto.getId());
        Double goldLoanDueAmount = apiGatewayService.getGoldLoanDueAmount(basicDetailsDto.getId());
        dueAmount += creditCardDueAmount + goldLoanDueAmount;
        responseMap.put("due_amount", dueAmount);
        cacheDueAmtData(dueAmount,dueAmountCacheKey,dueAmountCachingWindow);
        return new CommonResponse(responseMap);
    }

    private void cacheDueAmtData(Double dueAmt, String key, int ttl) {
        try {
            AddCacheDto addCacheDto = new AddCacheDto();
            addCacheDto.setKey(key);
            addCacheDto.setValue(dueAmt);
            addCacheDto.setTtl(ttl);
            lendingCache.add(addCacheDto, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.error("exception occured while caching loan details for {} !!", key);
        }
    }

    public CommonResponse checkMerchant(String mobile, String pancard) {
        try {
            logger.info("Request to check merchant for mobile:{} and pancard:{}", mobile, pancard);
            boolean isBpMerchant = false;
            Optional<BasicDetailsDto> basicDetailsDto = null;
            if (!StringUtils.isEmpty(mobile)) {
                if (mobile.length() == 10) {
                    mobile = "91" + mobile;
                }
//                merchant = merchantDao.findByMobile(mobile);
                basicDetailsDto = merchantService.fetchMerchantBasicDetailsByMobile(mobile);
            }
            if (basicDetailsDto == null && !StringUtils.isEmpty(pancard)) {
                Experian experian = experianDao.findByPancard(pancard);
                if (experian != null) {
//                    merchant = merchantDao.getById(experian.getMerchantId());
                    basicDetailsDto = merchantService.fetchMerchantBasicDetails(experian.getMerchantId());
                }
            }
            if (basicDetailsDto != null && basicDetailsDto.isPresent()) {
                List<LendingPaymentSchedule> lendingPaymentScheduleList = lendingPaymentScheduleDao.findPreviousLoansByMerchantAndCreditLoan(basicDetailsDto.get().getId(), false);
                for (LendingPaymentSchedule lendingPaymentSchedule : lendingPaymentScheduleList) {
                    BigInteger maxDpd = loanDpdDao.findMaxDpd(lendingPaymentSchedule.getId());
                    if (maxDpd.intValue() >= 20) {
                        isBpMerchant = true;
                        break;
                    }
                    if (LoanUtil.getDateDiffInDays(lendingPaymentSchedule.getCreatedAt(), new Date()) <= 30) {
                        isBpMerchant = true;
                        break;
                    }
                    if (LoanUtil.calculateDPD(lendingPaymentSchedule.getEdiAmount(), lendingPaymentSchedule.getDueAmount()) > 5) {
                        isBpMerchant = true;
                        break;
                    }
                }
                if (!isBpMerchant) {
                    LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(basicDetailsDto.get().getId(), "rejected");
                    if (lendingApplication != null && LoanUtil.getDateDiffInDays(lendingApplication.getUpdatedAt(), new Date()) <= 30) {
                        isBpMerchant = true;
                    }
                }
            }
            if (isBpMerchant) {
                return new CommonResponse(true, "BP Merchant");
            }
        } catch (Exception e) {
            logger.error("Exception while checking merchant", e);
        }
        return new CommonResponse(false, "merchant not found");
    }
}
