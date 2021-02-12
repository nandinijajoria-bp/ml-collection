package com.bharatpe.lending.service;


import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.LendingDisbursalStageDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.Status;
import com.bharatpe.lending.common.dao.BharatPeEnachDao;
import com.bharatpe.lending.common.dao.CreditLineMerchantDao;
import com.bharatpe.lending.common.dao.LendingApplicationPriorityDao;
import com.bharatpe.lending.common.entity.CreditLineMerchant;
import com.bharatpe.lending.common.entity.LendingApplicationPriority;
import com.bharatpe.lending.constant.SupportConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.SupportLoanResponseDTO;
import com.bharatpe.lending.dto.SupportResponseDTO;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.util.LoanUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class SupportService {
    private final Logger logger = LoggerFactory.getLogger(SupportService.class);

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    BharatPeEnachDao bharatPeEnachDao;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    @Autowired
    LendingDisbursalStageDao lendingDisbursalStageDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    MerchantBankDetailDao merchantBankDetailDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingApplicationPriorityDao lendingApplicationPriorityDao;

    @Autowired
    CreditLineMerchantDao creditLineMerchantDao;

    @Autowired
    LoanUtil loanUtil;

    public SupportResponseDTO supportLoan(Long merchantId) {

        SupportResponseDTO responseDTO = new SupportResponseDTO(true, "OK");
        try {
            SupportLoanResponseDTO supportLoanResponseDTO = new SupportLoanResponseDTO();
            supportLoanResponseDTO.setCreditLineAccount(Boolean.FALSE);
            CreditLineMerchant creditLineMerchant = creditLineMerchantDao.findByMerchantId(merchantId);
            if (!ObjectUtils.isEmpty(creditLineMerchant)) {
                supportLoanResponseDTO.setMessage(SupportConstants.ACTIVE_CREDIT_LINE);
                supportLoanResponseDTO.setCreditLineAccount(Boolean.TRUE);
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }
            supportLoanResponseDTO.setMerchantId(merchantId);
            supportLoanResponseDTO.setActiveLoan(Boolean.FALSE);
            supportLoanResponseDTO.setApplied(Boolean.FALSE);
            supportLoanResponseDTO.setExperian(Boolean.TRUE);

            supportLoanResponseDTO = getLoanDetail(supportLoanResponseDTO, merchantId);

            Experian experian = experianDao.getByMerchantId(merchantId);
            if (ObjectUtils.isEmpty(experian)) {
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.PAN_NOT_ENTERED);
                supportLoanResponseDTO.setMessage(SupportConstants.PAN_NOT_ENTERED_MESSAGE);
                supportLoanResponseDTO.setConditionalMessage("NA");
                supportLoanResponseDTO.setExperian(Boolean.FALSE);
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            if (experian.getRejected()) {
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.NOT_ELIGIBLE);
                supportLoanResponseDTO.setMessage("NA");
                supportLoanResponseDTO.setConditionalMessage(experian.getReason() != null ? getExperianReason(experian.getReason()) : getExperianReason(""));
                supportLoanResponseDTO.setEligible(Boolean.FALSE);
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            if (!StringUtils.isEmpty(experian.getReason())) {
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.NOT_ELIGIBLE);
                supportLoanResponseDTO.setMessage("NA");
                supportLoanResponseDTO.setConditionalMessage(getExperianReason(experian.getReason()));
                supportLoanResponseDTO.setEligible(Boolean.FALSE);
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            EligibleLoan eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if (ObjectUtils.isEmpty(eligibleLoan)) {
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.NOT_ELIGIBLE);
                supportLoanResponseDTO.setMessage("NA");
                supportLoanResponseDTO.setConditionalMessage(SupportConstants.NOT_ELIGIBLE_MESSAGE);
                supportLoanResponseDTO.setEligible(Boolean.FALSE);
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findLatestLendingPaymentScheduleByMerchantId(merchantId);
            LendingApplication lendingApplication = lendingApplicationDao.findTopByMerchantIdAndLoanDisbursalStatusNullOrderByIdDesc(merchantId);
            if (!ObjectUtils.isEmpty(lendingPaymentSchedule) && "CLOSED".equalsIgnoreCase(lendingPaymentSchedule.getStatus()) && ObjectUtils.isEmpty(lendingApplication)) {
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.NOT_APPLIED);
                supportLoanResponseDTO.setMessage("NA");
                supportLoanResponseDTO.setConditionalMessage(SupportConstants.NOT_APPLIED_MESSAGE);
                supportLoanResponseDTO.setEligible(null);
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            if (!ObjectUtils.isEmpty(lendingPaymentSchedule) && "INACTIVE".equalsIgnoreCase(lendingPaymentSchedule.getStatus()) && ObjectUtils.isEmpty(lendingApplication)) {
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.NOT_APPLIED);
                supportLoanResponseDTO.setMessage("NA");
                supportLoanResponseDTO.setConditionalMessage(SupportConstants.INACTIVE_LOAN_MESSAGE);
                supportLoanResponseDTO.setEligible(null);
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            if (ObjectUtils.isEmpty(lendingPaymentSchedule) && ObjectUtils.isEmpty(lendingApplication)) {
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.ELIGIBLE_NOT_APPLIED);
                supportLoanResponseDTO.setMessage(SupportConstants.ELIGIBLE_NOT_APPLIED_MESSAGE);
                supportLoanResponseDTO.setConditionalMessage("NA");
                supportLoanResponseDTO.setEligible(Boolean.TRUE);
                supportLoanResponseDTO.setApplied(Boolean.FALSE);

                SupportLoanResponseDTO.Eligibility eligibility = new SupportLoanResponseDTO.Eligibility();
                eligibility.setLoanAmount(eligibleLoan.getAmount());
                eligibility.setEdiAmount(eligibleLoan.getEdi());
                eligibility.setRepayment(eligibleLoan.getRepayment());
                eligibility.setTenure(eligibleLoan.getTenure());
                supportLoanResponseDTO.setEligibility(eligibility);
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            if (ObjectUtils.isEmpty(lendingApplication)) {
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            supportLoanResponseDTO.setEligible(Boolean.TRUE);
            supportLoanResponseDTO.setApplied(Boolean.TRUE);
            SupportLoanResponseDTO.LoanApplication loanApplication = new SupportLoanResponseDTO.LoanApplication();
            loanApplication.setApplicationSubmittedDate(lendingApplication.getAgreementAt());
            loanApplication.setLoanId(lendingApplication.getExternalLoanId());
            loanApplication.setLoanAmount(lendingApplication.getLoanAmount());
            loanApplication.setEdiAmount(lendingApplication.getEdi());
            loanApplication.setTenure(lendingApplication.getTenure());
            loanApplication.setInterestRate(lendingApplication.getInterestRate());
            loanApplication.setRepayment(lendingApplication.getRepayment());
            supportLoanResponseDTO.setLoanApplication(loanApplication);
            MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingApplication.getMerchant().getId(), Status.GeneralStatus.ACTIVE.name());
            if (!ObjectUtils.isEmpty(merchantBankDetail)) {
                supportLoanResponseDTO.setBeneficiaryName(merchantBankDetail.getBeneficiaryName());
                String bankAccount = merchantBankDetail.getAccountNumber();
                supportLoanResponseDTO.setBankAccount(new StringBuilder(bankAccount).replace(0, bankAccount.length() - 4, new String(new char[bankAccount.length() - 4]).replace("\0", "X")).toString());
            }
            supportLoanResponseDTO.setMobile(lendingApplication.getMerchant().getMobile());
            supportLoanResponseDTO.setCity(lendingApplication.getCity());
            supportLoanResponseDTO.setBusinessName(lendingApplication.getBusinessName());
            supportLoanResponseDTO.seteNachDone(ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getNachStatus()) ? Boolean.TRUE : Boolean.FALSE);

            String loanType = lendingApplication.getLoanType();

            Boolean nachMandatory = Boolean.FALSE;
            supportLoanResponseDTO.setNachMandatory(false);
            LendingApplicationPriority lendingApplicationPriority = lendingApplicationPriorityDao.findByApplicationId(lendingApplication.getId());
            if (ObjectUtils.isEmpty(lendingApplicationPriority)) {
                if ("NTB".equalsIgnoreCase(loanType) || "OGL".equalsIgnoreCase(loanType) || "BHARAT_SWIPE".equalsIgnoreCase(loanType) || ("REGULAR".equalsIgnoreCase(lendingApplication.getLoanType()) && 50000D > lendingApplication.getLoanAmount())) {
                    supportLoanResponseDTO.setApplicationStatus(SupportConstants.ENACH_PENDING);
                    supportLoanResponseDTO.setMessage(SupportConstants.ENACH_PENDING_MESSAGE);
                    supportLoanResponseDTO.setConditionalMessage("NA");
                    nachMandatory = Boolean.TRUE;
                }
            }

            boolean isLowPriority = lendingApplicationPriority != null && (ObjectUtils.isEmpty(lendingApplicationPriority) || lendingApplicationPriority.getCurrentPriority().equals("P4") || lendingApplicationPriority.getCurrentPriority().equals("P5") || lendingApplicationPriority.getCurrentPriority().equals("P6"));

            supportLoanResponseDTO.setNachMandatory(nachMandatory);
            if (nachMandatory) {
                if (!ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getNachStatus())) {
                    supportLoanResponseDTO.setApplicationStatus(SupportConstants.ENACH_PENDING);
                    supportLoanResponseDTO.setMessage(SupportConstants.ENACH_PENDING_MESSAGE);
                    supportLoanResponseDTO.setConditionalMessage("NA");
                    responseDTO.setData(supportLoanResponseDTO);
                    return responseDTO;
                }
            }

            if (ApplicationStatus.DRAFT.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.STARTED_APPLICATION_NOT_SUBMITTED);
                supportLoanResponseDTO.setMessage(SupportConstants.STARTED_APPLICATION_NOT_SUBMITTED_MESSAGE);
                supportLoanResponseDTO.setConditionalMessage("NA");
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            if (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(lendingApplication.getStatus())) {

                if (!ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getManualKyc()) && !ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getManualKyc())) {
                    supportLoanResponseDTO.setApplicationStatus(SupportConstants.KYC_VERIFICATION);
                    if(!isLowPriority) {
                        supportLoanResponseDTO.setMessage("NA");
                        supportLoanResponseDTO.setConditionalMessage(SupportConstants.KYC_VERIFICATION_PENDING.replace("<Priority_Message>", getPriorityMessage(lendingApplicationPriority, lendingApplication)));
                    } else {
                        supportLoanResponseDTO.setMessage(getPriorityMessage(lendingApplicationPriority, lendingApplication));
                        supportLoanResponseDTO.setConditionalMessage("NA");
                    }
                }

                if (ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getManualKyc())) {
                    supportLoanResponseDTO.setApplicationStatus(SupportConstants.KYC_VERIFICATION);
                    if(!isLowPriority) {
                        supportLoanResponseDTO.setMessage("NA");
                        supportLoanResponseDTO.setConditionalMessage(SupportConstants.KYC_VERIFICATION_APPROVED.replace("<Priority_Message>", getPriorityMessage(lendingApplicationPriority, lendingApplication)));
                    } else {
                        supportLoanResponseDTO.setMessage(getPriorityMessage(lendingApplicationPriority, lendingApplication));
                        supportLoanResponseDTO.setConditionalMessage("NA");
                    }

                    if (!ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus()) && !ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus()) && !"PENDING".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                        supportLoanResponseDTO.setApplicationStatus(SupportConstants.CPV_VERIFICATION);
                        if(!isLowPriority) {
                            supportLoanResponseDTO.setMessage(SupportConstants.CPV_VERIFICATION_MESSAGE);
                            supportLoanResponseDTO.setConditionalMessage(getPriorityMessage(lendingApplicationPriority, lendingApplication));
                        } else {
                            supportLoanResponseDTO.setMessage(getPriorityMessage(lendingApplicationPriority, lendingApplication));
                            supportLoanResponseDTO.setConditionalMessage("NA");
                        }
                    }

                    if ("PENDING".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                        supportLoanResponseDTO.setApplicationStatus(SupportConstants.CPV_VERIFICATION);
                        if(!isLowPriority){
                            supportLoanResponseDTO.setMessage("NA");
                            supportLoanResponseDTO.setConditionalMessage(SupportConstants.CPV_VERIFICATION_PENDING.replace("<Priority_Message>", getPriorityMessage(lendingApplicationPriority, lendingApplication)));
                        } else {
                            supportLoanResponseDTO.setMessage(getPriorityMessage(lendingApplicationPriority, lendingApplication));
                            supportLoanResponseDTO.setConditionalMessage("NA");
                        }
                    }

                    if (ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                        supportLoanResponseDTO.setApplicationStatus(SupportConstants.CPV_VERIFICATION);
                        if(!isLowPriority){
                            supportLoanResponseDTO.setMessage("NA");
                            supportLoanResponseDTO.setConditionalMessage(SupportConstants.CPV_VERIFICATION_APPROVED.replace("<Priority_Message>", getPriorityMessage(lendingApplicationPriority, lendingApplication)));
                        } else {
                            supportLoanResponseDTO.setMessage(getPriorityMessage(lendingApplicationPriority, lendingApplication));
                            supportLoanResponseDTO.setConditionalMessage("NA");
                        }

                        if (!ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getManualCibil()) && !ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getManualCibil())) {
                            supportLoanResponseDTO.setApplicationStatus(SupportConstants.CIBIL_VERIFICATION);
                            supportLoanResponseDTO.setMessage(SupportConstants.CIBIL_VERIFICATION_MESSAGE);
                            supportLoanResponseDTO.setConditionalMessage("NA");
                        }
                    }
                }
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            if (ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.APPROVED_VERIFICATION_CALLING_PENDING);
                supportLoanResponseDTO.setMessage(SupportConstants.APPROVED_VERIFICATION_CALLING_PENDING_MESSAGE);
                supportLoanResponseDTO.setConditionalMessage("NA");

                LendingDisbursalStage lendingDisbursalStage = lendingDisbursalStageDao.findByApplicationId(lendingApplication.getId());
                if (!ObjectUtils.isEmpty(lendingDisbursalStage)) {
                    if ("YES".equalsIgnoreCase(lendingDisbursalStage.getReadyStage())) {
                        supportLoanResponseDTO.setApplicationStatus(SupportConstants.VERIFICATION_CALLING_READY);
                        if(!isLowPriority) {
                            supportLoanResponseDTO.setMessage(SupportConstants.VERIFICATION_CALLING_READY_MESSAGE.replace("<Priority_Message>", getPriorityMessage(lendingApplicationPriority, lendingApplication)));
                            supportLoanResponseDTO.setConditionalMessage("NA");
                        } else {
                            supportLoanResponseDTO.setMessage(getPriorityMessage(lendingApplicationPriority, lendingApplication));
                            supportLoanResponseDTO.setConditionalMessage("NA");
                        }
                    }

                    if ("NTB".equalsIgnoreCase(lendingApplication.getLoanType())) {
                        if (!StringUtils.isEmpty(lendingDisbursalStage.getCallStage()) && !"YES".equalsIgnoreCase(lendingDisbursalStage.getCallStage()) && !"NO".equalsIgnoreCase(lendingDisbursalStage.getCallStage())) {
                            supportLoanResponseDTO.setApplicationStatus(SupportConstants.NTB_VERIFICATION_CALLING_PENDING);
                            if(!isLowPriority) {
                                supportLoanResponseDTO.setMessage(SupportConstants.NTB_VERIFICATION_CALLING_PENDING_MESSAGE.replace("<Priority_Message>", getPriorityMessage(lendingApplicationPriority, lendingApplication)));
                                supportLoanResponseDTO.setConditionalMessage("NA");
                            }  else {
                                supportLoanResponseDTO.setMessage(getPriorityMessage(lendingApplicationPriority, lendingApplication));
                                supportLoanResponseDTO.setConditionalMessage("NA");
                            }
                        }
                    }
                }

                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                supportLoanResponseDTO.setApplicationStatus(ApplicationStatus.REJECTED.name());
                supportLoanResponseDTO.setMessage(SupportConstants.REJECTED_MESSAGE);
                supportLoanResponseDTO.setConditionalMessage("NA");

                if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getManualKyc())) {
                    supportLoanResponseDTO.setMessage("NA");
                    supportLoanResponseDTO.setConditionalMessage(SupportConstants.KYC_VERIFICATION_REJECTED);
                }

                if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                    supportLoanResponseDTO.setMessage("NA");
                    supportLoanResponseDTO.setConditionalMessage(SupportConstants.CPV_VERIFICATION_REJECTED);
                }

                if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getManualCibil())) {
                    supportLoanResponseDTO.setMessage(SupportConstants.CIBIL_VERIFICATION_REJECTED);
                    supportLoanResponseDTO.setConditionalMessage("NA");
                }

                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            responseDTO.setData(supportLoanResponseDTO);
            return responseDTO;

        } catch (Exception ex) {
            logger.error("Exception while fetching the merchant loan details for merchant Id : {}, exception is: {} ", merchantId, ex);
            SupportLoanResponseDTO supportLoanResponseDTO = new SupportLoanResponseDTO();
            responseDTO.setData(supportLoanResponseDTO);
            return responseDTO;
        }
    }

    private String getExperianReason(String reason) {
        switch (reason) {
            case "ENACH":
                return SupportConstants.ENACH_MESSAGE;

            case "OGL":
                return SupportConstants.OGL_MESSAGE;

            case "FRAUD":
                return SupportConstants.FRAUD_MESSAGE;

            default:
                return SupportConstants.DEFAULT_EXPERIAN_REASON;
        }
    }

    private String getPriorityMessage(LendingApplicationPriority lendingApplicationPriority, LendingApplication lendingApplication) {

        if (ObjectUtils.isEmpty(lendingApplicationPriority)) {
            return SupportConstants.DEFAULT_PRIORITY;
        }

        Integer tat = loanUtil.getApplicationTAT(lendingApplication.getId());

        switch (lendingApplicationPriority.getCurrentPriority()) {
            case "P0":

                return tat < 1 ? SupportConstants.P0.replace(SupportConstants.P0,SupportConstants.TAT0_MESSAGE ) : SupportConstants.P0.replace("<current_TaT>", tat + "-" + (tat+2) + " Days");

            case "P1":
                return tat < 1 ? SupportConstants.P1.replace(SupportConstants.P1,SupportConstants.TAT0_MESSAGE ) : SupportConstants.P1.replace("<current_TaT>", tat + "-" + (tat+2) + " Days");

            case "P2":
                return tat < 1 ? SupportConstants.P2.replace(SupportConstants.P2,SupportConstants.TAT0_MESSAGE ) : SupportConstants.P2.replace("<current_TaT>", tat + "-" + (tat+2) + " Days");

            case "P3":
                return tat < 1 ? SupportConstants.P3.replace(SupportConstants.P3,SupportConstants.TAT0_MESSAGE ) : SupportConstants.P3.replace("<current_TaT>", tat + "-" + (tat+2) + " Days");

            case "P4":
                return SupportConstants.P4;

            case "P5":
                return SupportConstants.P5;

            default:
                return SupportConstants.DEFAULT_PRIORITY;
        }
    }

    private SupportLoanResponseDTO getLoanDetail(SupportLoanResponseDTO supportLoanResponseDTO, Long merchantId) {
        LendingApplication lendingApplication = lendingApplicationDao.findByMerchantIdAndStatus(merchantId, ApplicationStatus.APPROVED.name());
        if (ObjectUtils.isEmpty(lendingApplication)) {
            return supportLoanResponseDTO;
        }

        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndApplicationId(merchantId, lendingApplication.getId());
        if (!ObjectUtils.isEmpty(lendingPaymentSchedule)) {
            if (Status.GeneralStatus.ACTIVE.name().equalsIgnoreCase(lendingPaymentSchedule.getStatus())) {
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.ACTIVE_LOAN);
                supportLoanResponseDTO.setMessage(SupportConstants.ACTIVE_LOAN_MESSAGE);
                supportLoanResponseDTO.setConditionalMessage(SupportConstants.ACTIVE_LOAN_CONDITIONAL_MESSAGE);
                supportLoanResponseDTO.setActiveLoan(Boolean.TRUE);
            }

            List<Map<String, Object>> loanHistoryList = new ArrayList<>();
            List<LendingPaymentSchedule> lendingPaymentScheduleNew = lendingPaymentScheduleDao.findPreviousLoansByMerchantAndCreditLoan(merchantId, Boolean.FALSE);
            for (LendingPaymentSchedule lendingPaymentSchedule1 : lendingPaymentScheduleNew) {

                List<Map<String, Object>> lendingLedgerDetailList = new ArrayList<>();
                List<LendingLedger> lendingLedgerList = lendingLedgerDao.findByLendingPaymentScheduleOrderByDateDescAmountAsc(lendingPaymentSchedule1);
                for (LendingLedger lendingLedger1 : lendingLedgerList) {
                    Map<String, Object> lendingLedgerDetail = new HashMap<>();
                    lendingLedgerDetail.put("amount", lendingLedger1.getAmount());
                    lendingLedgerDetail.put("createdAt", lendingLedger1.getDate().toString());
                    lendingLedgerDetail.put("id", lendingLedger1.getId());
                    lendingLedgerDetail.put("transactionType", lendingLedger1.getTxnType());
                    lendingLedgerDetailList.add(lendingLedgerDetail);
                }

                Map<String, Object> loanDetails = new HashMap<>();
                loanDetails.put("agreementAt", lendingPaymentSchedule1.getLoanApplication().getAgreementAt());
                loanDetails.put("loanStatus", lendingPaymentSchedule1.getStatus());
                loanDetails.put("closingDate", lendingPaymentSchedule1.getClosingDate());
                loanDetails.put("disbursalAmount", lendingPaymentSchedule1.getLoanApplication().getDisbursalAmount());
                loanDetails.put("edi", lendingPaymentSchedule1.getLoanApplication().getEdi());
                loanDetails.put("ediRemainingCount", lendingPaymentSchedule1.getEdiRemainingCount());
                loanDetails.put("externalLoanId", lendingPaymentSchedule1.getLoanApplication().getExternalLoanId());
                loanDetails.put("id", lendingPaymentSchedule1.getLoanApplication().getId());
                loanDetails.put("interestRate", lendingPaymentSchedule1.getLoanApplication().getInterestRate());
                loanDetails.put("loanAmount", lendingPaymentSchedule1.getLoanApplication().getLoanAmount());
                loanDetails.put("nextEdiDate", lendingPaymentSchedule1.getNextEdiDate());
                loanDetails.put("paidAmount", lendingPaymentSchedule1.getPaidAmount());
                loanDetails.put("processingFee", lendingPaymentSchedule1.getLoanApplication().getProcessingFee());
                loanDetails.put("repayment", lendingPaymentSchedule1.getLoanApplication().getRepayment());
                loanDetails.put("tentativeClosingDate", lendingPaymentSchedule1.getTentativeClosingDate());
                loanDetails.put("tenure", lendingPaymentSchedule1.getLoanApplication().getTenure());
                loanDetails.put("ledgerDetails", lendingLedgerDetailList);

                loanHistoryList.add(loanDetails);
            }
            supportLoanResponseDTO.setLoanDetailsList(loanHistoryList);
            return supportLoanResponseDTO;

        }
        return supportLoanResponseDTO;
    }
}
