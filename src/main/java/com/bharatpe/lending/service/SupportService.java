package com.bharatpe.lending.service;


import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.dao.ExperianDao;
import com.bharatpe.common.dao.LendingDisbursalStageDao;
import com.bharatpe.common.dao.MerchantBankDetailDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.common.enums.Status;
import com.bharatpe.common.handlers.EmailHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.dao.BharatPeEnachDao;
import com.bharatpe.lending.common.dao.CreditLineMerchantDao;
import com.bharatpe.lending.common.dao.LendingApplicationPriorityDao;
import com.bharatpe.lending.common.dao.LendingBulkDisbursalRawDataDao;
import com.bharatpe.lending.common.entity.CreditLineMerchant;
import com.bharatpe.lending.common.entity.LendingApplicationPriority;
import com.bharatpe.lending.constant.SupportConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dto.CommonResponse;
import com.bharatpe.lending.dto.SupportLoanResponseDTO;
import com.bharatpe.lending.dto.SupportResponseDTO;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.util.LoanUtil;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    LendingEkycDao lendingEkycDao;

    @Autowired
    MerchantBankDetailDao merchantBankDetailDao;

    @Autowired
    LendingLedgerDao lendingLedgerDao;

    @Autowired
    LendingEdiScheduleService lendingEdiScheduleService;

    @Autowired
    EmailHandler emailHandler;

    @Autowired
    LendingBulkDisbursalRawDataDao lendingBulkDisbursalRawDataDao;

    @Autowired
    LendingApplicationPriorityDao lendingApplicationPriorityDao;

    @Autowired
    CreditLineMerchantDao creditLineMerchantDao;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    LoanUtil loanUtil;

    @Autowired
    LendingPayoutsDao lendingPayoutsDao;

    @Autowired
    LendingBulkDisbursalDao lendingBulkDisbursalDao;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    LoanDpdDao loanDpdDao;

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
                logger.info("CreditLine merchant found for merchantId: {}", merchantId);
                return responseDTO;
            }
            supportLoanResponseDTO.setMerchantId(merchantId);
            supportLoanResponseDTO.setActiveLoan(Boolean.FALSE);
            supportLoanResponseDTO.setApplied(Boolean.FALSE);
            supportLoanResponseDTO.setExperian(Boolean.TRUE);

            supportLoanResponseDTO = getLoanDetail(supportLoanResponseDTO, merchantId);

            Experian experian = experianDao.getByMerchantId(merchantId);
            if (ObjectUtils.isEmpty(experian)) {
                logger.info("PAN not entered so eligibility is not checked for the merchantId: {}", merchantId);
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.PAN_NOT_ENTERED);
                supportLoanResponseDTO.setMessage(SupportConstants.PAN_NOT_ENTERED_MESSAGE);
                supportLoanResponseDTO.setConditionalMessage("NA");
                supportLoanResponseDTO.setExperian(Boolean.FALSE);
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            if (experian.getRejected()) {
                logger.info("Experian Rejected for merchantId: {}, experianId: {}", merchantId, experian.getId());
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.NOT_ELIGIBLE);
                supportLoanResponseDTO.setMessage("NA");
                supportLoanResponseDTO.setConditionalMessage(experian.getReason() != null ? getExperianReason(experian.getReason()) : getExperianReason(""));
                supportLoanResponseDTO.setEligible(Boolean.FALSE);
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            if (!StringUtils.isEmpty(experian.getReason())) {
                logger.info("Experian Rejected Reason found for merchanID: {}, Reason: {}", merchantId, experian.getReason());
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.NOT_ELIGIBLE);
                supportLoanResponseDTO.setMessage("NA");
                supportLoanResponseDTO.setConditionalMessage(getExperianReason(experian.getReason()));
                supportLoanResponseDTO.setEligible(Boolean.FALSE);
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            EligibleLoan eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if (ObjectUtils.isEmpty(eligibleLoan)) {
                logger.info("Eligible loan offer not found for merchantId: {}", merchantId);
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
                logger.info("Previous Loan status is CLOSED and new loan application not found for merchantId: {}", merchantId);
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.NOT_APPLIED);
                supportLoanResponseDTO.setMessage("NA");
                supportLoanResponseDTO.setConditionalMessage(SupportConstants.NOT_APPLIED_MESSAGE);
                supportLoanResponseDTO.setEligible(null);
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            if (!ObjectUtils.isEmpty(lendingPaymentSchedule) && "INACTIVE".equalsIgnoreCase(lendingPaymentSchedule.getStatus()) && ObjectUtils.isEmpty(lendingApplication)) {
                logger.info("Previous Loan status is INACTIVE and new loan application not found for merchantId: {}", merchantId);
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.NOT_APPLIED);
                supportLoanResponseDTO.setMessage("NA");
                supportLoanResponseDTO.setConditionalMessage(SupportConstants.INACTIVE_LOAN_MESSAGE);
                supportLoanResponseDTO.setEligible(null);
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            if (ObjectUtils.isEmpty(lendingPaymentSchedule) && ObjectUtils.isEmpty(lendingApplication)) {
                logger.info("Previous loan or loan application not found for merchantId: {}", merchantId);
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
                logger.info("Application not found with pending disbursal for merchantId: {}", merchantId);
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
            SupportLoanResponseDTO.LoanArrangerFee loanArrangerFee = new SupportLoanResponseDTO.LoanArrangerFee();
            loanArrangerFee.setFeeAmount(lendingApplication.getProcessingFee());
            supportLoanResponseDTO.setLoanArrangerFee(loanArrangerFee);
            supportLoanResponseDTO.setLoanApplication(loanApplication);
            MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingApplication.getMerchant().getId(), Status.GeneralStatus.ACTIVE.name());
            if (!ObjectUtils.isEmpty(merchantBankDetail)) {
                logger.info("Merchant Bank Details not found for merchantId: {}", merchantId);
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
            if (ObjectUtils.isEmpty(lendingApplicationPriority) && !ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                logger.info("Application priority not found for merchantId: {}, applicationId: {}", merchantId, lendingApplication.getId());
                logger.info("Application found with loan type: {}, and loan amount: {}, for merchantId: {}, and applicationId: {}", loanType, lendingApplication.getLoanAmount(), merchantId, lendingApplication.getId());
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.ENACH_PENDING);
                supportLoanResponseDTO.setMessage(SupportConstants.ENACH_PENDING_MESSAGE);
                supportLoanResponseDTO.setConditionalMessage("NA");
                nachMandatory = Boolean.TRUE;
            }

            boolean isLowPriority = lendingApplicationPriority != null && (ObjectUtils.isEmpty(lendingApplicationPriority) || lendingApplicationPriority.getCurrentPriority().equals("P4") || lendingApplicationPriority.getCurrentPriority().equals("P5") || lendingApplicationPriority.getCurrentPriority().equals("P6"));
            logger.info("Application priority is: {}, applicationId: {}, merchantId: {}", isLowPriority, lendingApplication.getId(), merchantId);

            supportLoanResponseDTO.setNachMandatory(nachMandatory);
            if (nachMandatory) {
                logger.info("ENACH is mandatory for merchantId: {} and applicationId: {}", merchantId, lendingApplication.getId());
                if (!ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getNachStatus())) {
                    supportLoanResponseDTO.setApplicationStatus(SupportConstants.ENACH_PENDING);
                    supportLoanResponseDTO.setMessage(SupportConstants.ENACH_PENDING_MESSAGE);
                    supportLoanResponseDTO.setConditionalMessage("NA");
                    responseDTO.setData(supportLoanResponseDTO);
                    return responseDTO;
                }
            }

            if (ApplicationStatus.DRAFT.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                logger.info("Application status is in DRAFT for merchantId: {}, and applicationId: {}", merchantId, lendingApplication.getId());
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.STARTED_APPLICATION_NOT_SUBMITTED);
                supportLoanResponseDTO.setMessage(SupportConstants.STARTED_APPLICATION_NOT_SUBMITTED_MESSAGE);
                supportLoanResponseDTO.setConditionalMessage("NA");
                responseDTO.setData(supportLoanResponseDTO);
                return responseDTO;
            }

            if (ApplicationStatus.PENDING_VERIFICATION.name().equalsIgnoreCase(lendingApplication.getStatus())) {

                if (!ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getManualKyc()) && !ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getManualKyc())) {
                    logger.info("Application Kyc Status is: {}, for merchantId: {}, applicationId: {}", lendingApplication.getManualKyc(), merchantId, lendingApplication.getId());
                    supportLoanResponseDTO.setApplicationStatus(SupportConstants.KYC_VERIFICATION);
                    if (!isLowPriority) {
                        supportLoanResponseDTO.setMessage("NA");
                        supportLoanResponseDTO.setConditionalMessage(SupportConstants.KYC_VERIFICATION_PENDING.replace("<Priority_Message>", getPriorityMessage(lendingApplicationPriority, lendingApplication)));
                    } else {
                        supportLoanResponseDTO.setMessage(getPriorityMessage(lendingApplicationPriority, lendingApplication));
                        supportLoanResponseDTO.setConditionalMessage("NA");
                    }
                }

                if (ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getManualKyc())) {
                    logger.info("Application Kyc Status is: {}, for merchantId: {}, applcationId: {}", lendingApplication.getManualKyc(), merchantId, lendingApplication.getId());
                    supportLoanResponseDTO.setApplicationStatus(SupportConstants.KYC_VERIFICATION);
                    if (!isLowPriority) {
                        supportLoanResponseDTO.setMessage("NA");
                        supportLoanResponseDTO.setConditionalMessage(SupportConstants.KYC_VERIFICATION_APPROVED.replace("<Priority_Message>", getPriorityMessage(lendingApplicationPriority, lendingApplication)));
                    } else {
                        supportLoanResponseDTO.setMessage(getPriorityMessage(lendingApplicationPriority, lendingApplication));
                        supportLoanResponseDTO.setConditionalMessage("NA");
                    }

                    if (!ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus()) && !ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus()) && !"PENDING".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                        logger.info("Application CPV Status is: {}, for merchantId: {}, applicationId: {}", lendingApplication.getPhysicalVerificationStatus(), merchantId, lendingApplication.getId());
                        supportLoanResponseDTO.setApplicationStatus(SupportConstants.CPV_VERIFICATION);
                        if (!isLowPriority) {
                            supportLoanResponseDTO.setMessage("NA");
                            supportLoanResponseDTO.setConditionalMessage(SupportConstants.CPV_VERIFICATION_MESSAGE.replace("<Priority_Message>", getPriorityMessage(lendingApplicationPriority, lendingApplication)));
                        } else {
                            supportLoanResponseDTO.setMessage(getPriorityMessage(lendingApplicationPriority, lendingApplication));
                            supportLoanResponseDTO.setConditionalMessage("NA");
                        }
                    }

                    if ("PENDING".equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                        logger.info("Application CPV Status is: {}, for merchantId: {}, applicationId: {}", lendingApplication.getPhysicalVerificationStatus(), merchantId, lendingApplication.getId());
                        supportLoanResponseDTO.setApplicationStatus(SupportConstants.CPV_VERIFICATION);
                        if (!isLowPriority) {
                            supportLoanResponseDTO.setMessage("NA");
                            supportLoanResponseDTO.setConditionalMessage(SupportConstants.CPV_VERIFICATION_PENDING.replace("<Priority_Message>", getPriorityMessage(lendingApplicationPriority, lendingApplication)));
                        } else {
                            supportLoanResponseDTO.setMessage(getPriorityMessage(lendingApplicationPriority, lendingApplication));
                            supportLoanResponseDTO.setConditionalMessage("NA");
                        }
                    }

                    if (ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                        logger.info("Application CPV Status is: {}, for merchantId: {}, applicationId: {}", lendingApplication.getPhysicalVerificationStatus(), merchantId, lendingApplication.getId());
                        supportLoanResponseDTO.setApplicationStatus(SupportConstants.CPV_VERIFICATION);
                        if (!isLowPriority) {
                            supportLoanResponseDTO.setMessage("NA");
                            supportLoanResponseDTO.setConditionalMessage(SupportConstants.CPV_VERIFICATION_APPROVED.replace("<Priority_Message>", getPriorityMessage(lendingApplicationPriority, lendingApplication)));
                        } else {
                            supportLoanResponseDTO.setMessage(getPriorityMessage(lendingApplicationPriority, lendingApplication));
                            supportLoanResponseDTO.setConditionalMessage("NA");
                        }

                        if (!ApplicationStatus.APPROVED.name().equalsIgnoreCase(lendingApplication.getManualCibil()) && !ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getManualCibil())) {
                            logger.info("Application CIBIL Status is: {}, for merchantId: {}, applicationId: {}", lendingApplication.getManualCibil(), merchantId, lendingApplication.getId());
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
                logger.info("Application Status is: {}, for merchantId: {}, applicationId: {}", lendingApplication.getStatus(), merchantId, lendingApplication.getId());
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.APPROVED_VERIFICATION_CALLING_PENDING);
                supportLoanResponseDTO.setMessage(SupportConstants.APPROVED_VERIFICATION_CALLING_PENDING_MESSAGE.replace("<Priority_Message>", getPriorityMessage(lendingApplicationPriority, lendingApplication)));
                supportLoanResponseDTO.setConditionalMessage("NA");

                LendingDisbursalStage lendingDisbursalStage = lendingDisbursalStageDao.findByApplicationId(lendingApplication.getId());
                if (!ObjectUtils.isEmpty(lendingDisbursalStage)) {
                    logger.info("Application ready stage status is: {}, for merchantId: {}, applicationId: {}", lendingDisbursalStage.getReadyStage(), merchantId, lendingApplication.getId());
                    if ("YES".equalsIgnoreCase(lendingDisbursalStage.getReadyStage())) {
                        supportLoanResponseDTO.setApplicationStatus(SupportConstants.VERIFICATION_CALLING_READY);
                        if (!isLowPriority) {
                            supportLoanResponseDTO.setMessage("NA");
                            supportLoanResponseDTO.setConditionalMessage(SupportConstants.VERIFICATION_CALLING_READY_MESSAGE.replace("<Priority_Message>", getPriorityMessage(lendingApplicationPriority, lendingApplication)));
                        } else {
                            supportLoanResponseDTO.setMessage(getPriorityMessage(lendingApplicationPriority, lendingApplication));
                            supportLoanResponseDTO.setConditionalMessage("NA");
                        }
                    }

                    if ("NTB".equalsIgnoreCase(lendingApplication.getLoanType())) {
                        logger.info("Application calling stage status is: {}, for merchantId: {}, applicationId: {}", lendingDisbursalStage.getCallStage(), merchantId, lendingApplication.getId());
                        if (!StringUtils.isEmpty(lendingDisbursalStage.getCallStage()) && !"YES".equalsIgnoreCase(lendingDisbursalStage.getCallStage()) && !"NO".equalsIgnoreCase(lendingDisbursalStage.getCallStage())) {
                            supportLoanResponseDTO.setApplicationStatus(SupportConstants.NTB_VERIFICATION_CALLING_PENDING);
                            if (!isLowPriority) {
                                supportLoanResponseDTO.setMessage("NA");
                                supportLoanResponseDTO.setConditionalMessage(SupportConstants.NTB_VERIFICATION_CALLING_PENDING_MESSAGE.replace("<Priority_Message>", getPriorityMessage(lendingApplicationPriority, lendingApplication)));
                            } else {
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
                logger.info("Application status is: {}, for merchantId: {}, applicationId: {}", lendingApplication.getStatus(), merchantId, lendingApplication.getId());
                supportLoanResponseDTO.setApplicationStatus(ApplicationStatus.REJECTED.name());
                supportLoanResponseDTO.setMessage(SupportConstants.REJECTED_MESSAGE);
                supportLoanResponseDTO.setConditionalMessage("NA");

                if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getManualKyc())) {
                    supportLoanResponseDTO.setMessage(SupportConstants.KYC_VERIFICATION_REJECTED);
                    supportLoanResponseDTO.setConditionalMessage("NA");
                }

                if (ApplicationStatus.REJECTED.name().equalsIgnoreCase(lendingApplication.getPhysicalVerificationStatus())) {
                    supportLoanResponseDTO.setMessage(SupportConstants.CPV_VERIFICATION_REJECTED);
                    supportLoanResponseDTO.setConditionalMessage("NA");
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

                return tat < 1 ? SupportConstants.P0.replace(SupportConstants.P0, SupportConstants.TAT0_MESSAGE) : SupportConstants.P0.replace("<current_TaT>", tat + "-" + (tat + 2) + " Days");

            case "P1":
                return tat < 1 ? SupportConstants.P1.replace(SupportConstants.P1, SupportConstants.TAT0_MESSAGE) : SupportConstants.P1.replace("<current_TaT>", tat + "-" + (tat + 2) + " Days");

            case "P2":
                return tat < 1 ? SupportConstants.P2.replace(SupportConstants.P2, SupportConstants.TAT0_MESSAGE) : SupportConstants.P2.replace("<current_TaT>", tat + "-" + (tat + 2) + " Days");

            case "P3":
                return tat < 1 ? SupportConstants.P3.replace(SupportConstants.P3, SupportConstants.TAT0_MESSAGE) : SupportConstants.P3.replace("<current_TaT>", tat + "-" + (tat + 2) + " Days");

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
            logger.info("No any APPROVED loan found for merchantId: {}", merchantId);
            return supportLoanResponseDTO;
        }

        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndApplicationId(merchantId, lendingApplication.getId());
        if (!ObjectUtils.isEmpty(lendingPaymentSchedule)) {
            if (Status.GeneralStatus.ACTIVE.name().equalsIgnoreCase(lendingPaymentSchedule.getStatus())) {
                logger.info("Active Loan found for merchantId: {}, and applicationId: {}", merchantId, lendingApplication.getId());
                supportLoanResponseDTO.setApplicationStatus(SupportConstants.ACTIVE_LOAN);
                supportLoanResponseDTO.setMessage(SupportConstants.ACTIVE_LOAN_MESSAGE);
                supportLoanResponseDTO.setConditionalMessage(SupportConstants.ACTIVE_LOAN_CONDITIONAL_MESSAGE);
                supportLoanResponseDTO.setActiveLoan(Boolean.TRUE);
            }

            List<Map<String, Object>> loanHistoryList = new ArrayList<>();
            List<LendingPaymentSchedule> lendingPaymentScheduleNew = lendingPaymentScheduleDao.findPreviousLoansByMerchantAndCreditLoan(merchantId, Boolean.FALSE);
            for (LendingPaymentSchedule lendingPaymentSchedule1 : lendingPaymentScheduleNew) {
                logger.info("Loan found in payment schedule for merchant id: {}, and applicationId: {}, with status: {}", merchantId, lendingPaymentSchedule1.getLoanApplication().getId(), lendingPaymentSchedule1.getStatus());
                List<Map<String, Object>> lendingLedgerDetailList = new ArrayList<>();
                List<LendingLedger> lendingLedgerList = lendingLedgerDao.findByLendingPaymentScheduleOrderByDateDescAmountAsc(lendingPaymentSchedule1);
                for (LendingLedger lendingLedger1 : lendingLedgerList) {
                    Map<String, Object> lendingLedgerDetail = new HashMap<>();
                    lendingLedgerDetail.put("amount", lendingLedger1.getAmount());
                    lendingLedgerDetail.put("createdAt", lendingLedger1.getDate() == null ? lendingLedger1.getCreatedAt().toString() : lendingLedger1.getDate().toString());
                    lendingLedgerDetail.put("id", lendingLedger1.getId());
                    lendingLedgerDetail.put("transactionType", lendingLedger1.getTxnType());
                    lendingLedgerDetailList.add(lendingLedgerDetail);
                }

                SupportLoanResponseDTO.LoanArrangerFee loanArrangerFee = new SupportLoanResponseDTO.LoanArrangerFee();
                loanArrangerFee.setFeeAmount(lendingPaymentSchedule1.getLoanApplication().getProcessingFee());
                LendingPayouts lendingPayouts = lendingPayoutsDao.findTopByMerchantIdAndOwnerIdAndStatusAndOrderIdLikeOrderByIdDesc(lendingPaymentSchedule1.getMerchant().getId(), lendingPaymentSchedule1.getId());
                if (!ObjectUtils.isEmpty(lendingPayouts)) {
                    loanArrangerFee.setArrangerFeeRefundEligible(true);
                    loanArrangerFee.setArrangerFeeRefunded(true);
                    loanArrangerFee.setTimestamp(lendingPayouts.getPaidAt());
                } else {
                    boolean eligible = isArrangerFeeEligible(lendingPaymentSchedule1);
                    loanArrangerFee.setArrangerFeeRefundEligible(eligible);
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
                loanDetails.put("loanArrangerFee", loanArrangerFee);

                loanHistoryList.add(loanDetails);
            }
            supportLoanResponseDTO.setLoanDetailsList(loanHistoryList);
            return supportLoanResponseDTO;

        }
        return supportLoanResponseDTO;
    }

    private Boolean isArrangerFeeEligible(LendingPaymentSchedule lendingPaymentSchedule) {
        if (lendingPaymentSchedule.getStatus().equals("CLOSED") && lendingPaymentSchedule.getLoanApplication() != null && lendingPaymentSchedule.getLoanApplication().getProcessingFee() != null && lendingPaymentSchedule.getLoanApplication().getProcessingFee() > 0D) {
            BigInteger maxDpd = loanDpdDao.findMaxDpd(lendingPaymentSchedule.getId());
            long dpd = LoanUtil.getDateDiffInDays(lendingPaymentSchedule.getTentativeClosingDate(), lendingPaymentSchedule.getClosingDate());
            LendingLedger lendingLedger = lendingLedgerDao.getForClosedLedger(lendingPaymentSchedule.getId());
            if (maxDpd.intValue() <= 5 && dpd <= 5 && (dpd >= -5 || Objects.isNull(lendingLedger))) {
                return true;
            }
        }
        return false;
    }

    public SupportResponseDTO bulkLenderchange(Long merchantId, Long applicationId, Long fileId, Boolean flag, String lender) {
        logger.info("Lender Change For Merchant Id:{}, and ApplicationId:{}", merchantId, applicationId);
        SupportResponseDTO responseDTO = new SupportResponseDTO(true, "OK");
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
            if (!"approved".equalsIgnoreCase(lendingApplication.getStatus()) || lendingApplication.getDisburseTimestamp() != null) {
                responseDTO.setSuccess(false);
                responseDTO.setMessage("Not Match Condition");
                return responseDTO;
            }
            new Thread(() -> {
                LdcVirtualAccount ldcVirtualAccount= apiGatewayService.createDisbursalVPA(lendingApplication.getMerchant(),lendingApplication);
                logger.info("ldc Virtual Accoint:{}",ldcVirtualAccount);
                if (flag) {
                    List<LendingBulkDisbursalRawData> lendingBulkDisbursalRawData = lendingBulkDisbursalRawDataDao.findByFileId(fileId);
                    File file = new File("/tmp/"+fileId+"_nbfc_details.csv");
                    try{
                        FileWriter outputfile = new FileWriter(file);
                        CSVWriter writer = new CSVWriter(outputfile);
                        List<String[]> data = new ArrayList<String[]>();
                        if("MAMTA".equalsIgnoreCase(lender)){
                            String[] header = { "partner_tag", "loan_type", "Loan_amount","tenure","partner_loan_id","fee_amount","gst_amount","interest_rate","interest_type","partner_computed_disbursement_amount","partner_computed_interest_amount","no_of_EDI","EDI_amount","EDI_schedule","customer_risk_segment","customer_location_category","existing_BP_merchant","customer_type_NTC","any_written_off_loan_in_last_two_years","income_to_debt_ratio","recommendation_from_BP","date_of_birth","consumer_name","gender","email","pan_number","mobile_number","loan_purpose","pincode","address","city","address_state","address_type","address_proof_type","type","stay_type","landmark","customer_bank_name","bank_account_number","customer_bank_account_name","ifsc_code","address_proof_1","address_proof_2","pan_card","loan_agreement","eKycResponse" };
                            data.add(header);
                            for (LendingBulkDisbursalRawData bulklender : lendingBulkDisbursalRawData) {
                                LendingApplication application = lendingApplicationDao.findByIdAndMerchantId(bulklender.getApplicationId(), bulklender.getMerchantId());
                                Experian experian = experianDao.getByMerchantId(bulklender.getMerchantId());
                                CommonResponse ediScheduleResponse = lendingEdiScheduleService.getEdiSchedule(application.getMerchant().getId(), application.getId());
                                String ediSchedule = ediScheduleResponse.getData().toString();
//                            String ediSchedule= "[{\"principal\":487.53,\"interest\":127.47,\"sl_no\":1,\"EDI_amount\":615},{\"principal\":488.26,\"interest\":126.74,\"sl_no\":2,\"EDI_amount\":615},{\"principal\":488.99,\"interest\":126.01,\"sl_no\":3,\"EDI_amount\":615},{\"principal\":489.73,\"interest\":125.27,\"sl_no\":4,\"EDI_amount\":615},{\"principal\":490.46,\"interest\":124.54,\"sl_no\":5,\"EDI_amount\":615},{\"principal\":491.2,\"interest\":123.8,\"sl_no\":6,\"EDI_amount\":615},{\"principal\":491.93,\"interest\":123.07,\"sl_no\":7,\"EDI_amount\":615},{\"principal\":492.67,\"interest\":122.33,\"sl_no\":8,\"EDI_amount\":615},{\"principal\":493.41,\"interest\":121.59,\"sl_no\":9,\"EDI_amount\":615},{\"principal\":494.15,\"interest\":120.85,\"sl_no\":10,\"EDI_amount\":615},{\"principal\":494.89,\"interest\":120.11,\"sl_no\":11,\"EDI_amount\":615},{\"principal\":495.63,\"interest\":119.37,\"sl_no\":12,\"EDI_amount\":615},{\"principal\":496.37,\"interest\":118.63,\"sl_no\":13,\"EDI_amount\":615},{\"principal\":497.12,\"interest\":117.88,\"sl_no\":14,\"EDI_amount\":615},{\"principal\":497.86,\"interest\":117.14,\"sl_no\":15,\"EDI_amount\":615},{\"principal\":498.61,\"interest\":116.39,\"sl_no\":16,\"EDI_amount\":615},{\"principal\":499.36,\"interest\":115.64,\"sl_no\":17,\"EDI_amount\":615},{\"principal\":500.11,\"interest\":114.89,\"sl_no\":18,\"EDI_amount\":615},{\"principal\":500.86,\"interest\":114.14,\"sl_no\":19,\"EDI_amount\":615},{\"principal\":501.61,\"interest\":113.39,\"sl_no\":20,\"EDI_amount\":615},{\"principal\":502.36,\"interest\":112.64,\"sl_no\":21,\"EDI_amount\":615},{\"principal\":503.11,\"interest\":111.89,\"sl_no\":22,\"EDI_amount\":615},{\"principal\":503.87,\"interest\":111.13,\"sl_no\":23,\"EDI_amount\":615},{\"principal\":504.62,\"interest\":110.38,\"sl_no\":24,\"EDI_amount\":615},{\"principal\":505.38,\"interest\":109.62,\"sl_no\":25,\"EDI_amount\":615},{\"principal\":506.14,\"interest\":108.86,\"sl_no\":26,\"EDI_amount\":615},{\"principal\":506.9,\"interest\":108.1,\"sl_no\":27,\"EDI_amount\":615},{\"principal\":507.66,\"interest\":107.34,\"sl_no\":28,\"EDI_amount\":615},{\"principal\":508.42,\"interest\":106.58,\"sl_no\":29,\"EDI_amount\":615},{\"principal\":509.18,\"interest\":105.82,\"sl_no\":30,\"EDI_amount\":615},{\"principal\":509.95,\"interest\":105.05,\"sl_no\":31,\"EDI_amount\":615},{\"principal\":510.71,\"interest\":104.29,\"sl_no\":32,\"EDI_amount\":615},{\"principal\":511.48,\"interest\":103.52,\"sl_no\":33,\"EDI_amount\":615},{\"principal\":512.24,\"interest\":102.76,\"sl_no\":34,\"EDI_amount\":615},{\"principal\":513.01,\"interest\":101.99,\"sl_no\":35,\"EDI_amount\":615},{\"principal\":513.78,\"interest\":101.22,\"sl_no\":36,\"EDI_amount\":615},{\"principal\":514.55,\"interest\":100.45,\"sl_no\":37,\"EDI_amount\":615},{\"principal\":515.32,\"interest\":99.68,\"sl_no\":38,\"EDI_amount\":615},{\"principal\":516.1,\"interest\":98.9,\"sl_no\":39,\"EDI_amount\":615},{\"principal\":516.87,\"interest\":98.13,\"sl_no\":40,\"EDI_amount\":615},{\"principal\":517.65,\"interest\":97.35,\"sl_no\":41,\"EDI_amount\":615},{\"principal\":518.42,\"interest\":96.58,\"sl_no\":42,\"EDI_amount\":615},{\"principal\":519.2,\"interest\":95.8,\"sl_no\":43,\"EDI_amount\":615},{\"principal\":519.98,\"interest\":95.02,\"sl_no\":44,\"EDI_amount\":615},{\"principal\":520.76,\"interest\":94.24,\"sl_no\":45,\"EDI_amount\":615},{\"principal\":521.54,\"interest\":93.46,\"sl_no\":46,\"EDI_amount\":615},{\"principal\":522.32,\"interest\":92.68,\"sl_no\":47,\"EDI_amount\":615},{\"principal\":523.1,\"interest\":91.9,\"sl_no\":48,\"EDI_amount\":615},{\"principal\":523.89,\"interest\":91.11,\"sl_no\":49,\"EDI_amount\":615},{\"principal\":524.67,\"interest\":90.33,\"sl_no\":50,\"EDI_amount\":615},{\"principal\":525.46,\"interest\":89.54,\"sl_no\":51,\"EDI_amount\":615},{\"principal\":526.25,\"interest\":88.75,\"sl_no\":52,\"EDI_amount\":615},{\"principal\":527.04,\"interest\":87.96,\"sl_no\":53,\"EDI_amount\":615},{\"principal\":527.83,\"interest\":87.17,\"sl_no\":54,\"EDI_amount\":615},{\"principal\":528.62,\"interest\":86.38,\"sl_no\":55,\"EDI_amount\":615},{\"principal\":529.41,\"interest\":85.59,\"sl_no\":56,\"EDI_amount\":615},{\"principal\":530.21,\"interest\":84.79,\"sl_no\":57,\"EDI_amount\":615},{\"principal\":531,\"interest\":84,\"sl_no\":58,\"EDI_amount\":615},{\"principal\":531.8,\"interest\":83.2,\"sl_no\":59,\"EDI_amount\":615},{\"principal\":532.6,\"interest\":82.4,\"sl_no\":60,\"EDI_amount\":615},{\"principal\":533.39,\"interest\":81.61,\"sl_no\":61,\"EDI_amount\":615},{\"principal\":534.19,\"interest\":80.81,\"sl_no\":62,\"EDI_amount\":615},{\"principal\":534.99,\"interest\":80.01,\"sl_no\":63,\"EDI_amount\":615},{\"principal\":535.8,\"interest\":79.2,\"sl_no\":64,\"EDI_amount\":615},{\"principal\":536.6,\"interest\":78.4,\"sl_no\":65,\"EDI_amount\":615},{\"principal\":537.41,\"interest\":77.59,\"sl_no\":66,\"EDI_amount\":615},{\"principal\":538.21,\"interest\":76.79,\"sl_no\":67,\"EDI_amount\":615},{\"principal\":539.02,\"interest\":75.98,\"sl_no\":68,\"EDI_amount\":615},{\"principal\":539.83,\"interest\":75.17,\"sl_no\":69,\"EDI_amount\":615},{\"principal\":540.64,\"interest\":74.36,\"sl_no\":70,\"EDI_amount\":615},{\"principal\":541.45,\"interest\":73.55,\"sl_no\":71,\"EDI_amount\":615},{\"principal\":542.26,\"interest\":72.74,\"sl_no\":72,\"EDI_amount\":615},{\"principal\":543.07,\"interest\":71.93,\"sl_no\":73,\"EDI_amount\":615},{\"principal\":543.89,\"interest\":71.11,\"sl_no\":74,\"EDI_amount\":615},{\"principal\":544.7,\"interest\":70.3,\"sl_no\":75,\"EDI_amount\":615},{\"principal\":545.52,\"interest\":69.48,\"sl_no\":76,\"EDI_amount\":615},{\"principal\":546.34,\"interest\":68.66,\"sl_no\":77,\"EDI_amount\":615},{\"principal\":547.16,\"interest\":67.84,\"sl_no\":78,\"EDI_amount\":615},{\"principal\":547.98,\"interest\":67.02,\"sl_no\":79,\"EDI_amount\":615},{\"principal\":548.8,\"interest\":66.2,\"sl_no\":80,\"EDI_amount\":615},{\"principal\":549.62,\"interest\":65.38,\"sl_no\":81,\"EDI_amount\":615},{\"principal\":550.45,\"interest\":64.55,\"sl_no\":82,\"EDI_amount\":615},{\"principal\":551.27,\"interest\":63.73,\"sl_no\":83,\"EDI_amount\":615},{\"principal\":552.1,\"interest\":62.9,\"sl_no\":84,\"EDI_amount\":615},{\"principal\":552.93,\"interest\":62.07,\"sl_no\":85,\"EDI_amount\":615},{\"principal\":553.76,\"interest\":61.24,\"sl_no\":86,\"EDI_amount\":615},{\"principal\":554.59,\"interest\":60.41,\"sl_no\":87,\"EDI_amount\":615},{\"principal\":555.42,\"interest\":59.58,\"sl_no\":88,\"EDI_amount\":615},{\"principal\":556.25,\"interest\":58.75,\"sl_no\":89,\"EDI_amount\":615},{\"principal\":557.08,\"interest\":57.92,\"sl_no\":90,\"EDI_amount\":615},{\"principal\":557.92,\"interest\":57.08,\"sl_no\":91,\"EDI_amount\":615},{\"principal\":558.76,\"interest\":56.24,\"sl_no\":92,\"EDI_amount\":615},{\"principal\":559.59,\"interest\":55.41,\"sl_no\":93,\"EDI_amount\":615},{\"principal\":560.43,\"interest\":54.57,\"sl_no\":94,\"EDI_amount\":615},{\"principal\":561.27,\"interest\":53.73,\"sl_no\":95,\"EDI_amount\":615},{\"principal\":562.12,\"interest\":52.88,\"sl_no\":96,\"EDI_amount\":615},{\"principal\":562.96,\"interest\":52.04,\"sl_no\":97,\"EDI_amount\":615},{\"principal\":563.8,\"interest\":51.2,\"sl_no\":98,\"EDI_amount\":615},{\"principal\":564.65,\"interest\":50.35,\"sl_no\":99,\"EDI_amount\":615},{\"principal\":565.5,\"interest\":49.5,\"sl_no\":100,\"EDI_amount\":615},{\"principal\":566.34,\"interest\":48.66,\"sl_no\":101,\"EDI_amount\":615},{\"principal\":567.19,\"interest\":47.81,\"sl_no\":102,\"EDI_amount\":615},{\"principal\":568.04,\"interest\":46.96,\"sl_no\":103,\"EDI_amount\":615},{\"principal\":568.9,\"interest\":46.1,\"sl_no\":104,\"EDI_amount\":615},{\"principal\":569.75,\"interest\":45.25,\"sl_no\":105,\"EDI_amount\":615},{\"principal\":570.6,\"interest\":44.4,\"sl_no\":106,\"EDI_amount\":615},{\"principal\":571.46,\"interest\":43.54,\"sl_no\":107,\"EDI_amount\":615},{\"principal\":572.32,\"interest\":42.68,\"sl_no\":108,\"EDI_amount\":615},{\"principal\":573.17,\"interest\":41.83,\"sl_no\":109,\"EDI_amount\":615},{\"principal\":574.03,\"interest\":40.97,\"sl_no\":110,\"EDI_amount\":615},{\"principal\":574.89,\"interest\":40.11,\"sl_no\":111,\"EDI_amount\":615},{\"principal\":575.76,\"interest\":39.24,\"sl_no\":112,\"EDI_amount\":615},{\"principal\":576.62,\"interest\":38.38,\"sl_no\":113,\"EDI_amount\":615},{\"principal\":577.48,\"interest\":37.52,\"sl_no\":114,\"EDI_amount\":615},{\"principal\":578.35,\"interest\":36.65,\"sl_no\":115,\"EDI_amount\":615},{\"principal\":579.22,\"interest\":35.78,\"sl_no\":116,\"EDI_amount\":615},{\"principal\":580.09,\"interest\":34.91,\"sl_no\":117,\"EDI_amount\":615},{\"principal\":580.96,\"interest\":34.04,\"sl_no\":118,\"EDI_amount\":615},{\"principal\":581.83,\"interest\":33.17,\"sl_no\":119,\"EDI_amount\":615},{\"principal\":582.7,\"interest\":32.3,\"sl_no\":120,\"EDI_amount\":615},{\"principal\":583.57,\"interest\":31.43,\"sl_no\":121,\"EDI_amount\":615},{\"principal\":584.45,\"interest\":30.55,\"sl_no\":122,\"EDI_amount\":615},{\"principal\":585.33,\"interest\":29.67,\"sl_no\":123,\"EDI_amount\":615},{\"principal\":586.2,\"interest\":28.8,\"sl_no\":124,\"EDI_amount\":615},{\"principal\":587.08,\"interest\":27.92,\"sl_no\":125,\"EDI_amount\":615},{\"principal\":587.96,\"interest\":27.04,\"sl_no\":126,\"EDI_amount\":615},{\"principal\":588.84,\"interest\":26.16,\"sl_no\":127,\"EDI_amount\":615},{\"principal\":589.73,\"interest\":25.27,\"sl_no\":128,\"EDI_amount\":615},{\"principal\":590.61,\"interest\":24.39,\"sl_no\":129,\"EDI_amount\":615},{\"principal\":591.5,\"interest\":23.5,\"sl_no\":130,\"EDI_amount\":615},{\"principal\":592.38,\"interest\":22.62,\"sl_no\":131,\"EDI_amount\":615},{\"principal\":593.27,\"interest\":21.73,\"sl_no\":132,\"EDI_amount\":615},{\"principal\":594.16,\"interest\":20.84,\"sl_no\":133,\"EDI_amount\":615},{\"principal\":595.05,\"interest\":19.95,\"sl_no\":134,\"EDI_amount\":615},{\"principal\":595.95,\"interest\":19.05,\"sl_no\":135,\"EDI_amount\":615},{\"principal\":596.84,\"interest\":18.16,\"sl_no\":136,\"EDI_amount\":615},{\"principal\":597.73,\"interest\":17.27,\"sl_no\":137,\"EDI_amount\":615},{\"principal\":598.63,\"interest\":16.37,\"sl_no\":138,\"EDI_amount\":615},{\"principal\":599.53,\"interest\":15.47,\"sl_no\":139,\"EDI_amount\":615},{\"principal\":600.43,\"interest\":14.57,\"sl_no\":140,\"EDI_amount\":615},{\"principal\":601.33,\"interest\":13.67,\"sl_no\":141,\"EDI_amount\":615},{\"principal\":602.23,\"interest\":12.77,\"sl_no\":142,\"EDI_amount\":615},{\"principal\":603.13,\"interest\":11.87,\"sl_no\":143,\"EDI_amount\":615},{\"principal\":604.04,\"interest\":10.96,\"sl_no\":144,\"EDI_amount\":615},{\"principal\":604.94,\"interest\":10.06,\"sl_no\":145,\"EDI_amount\":615},{\"principal\":605.85,\"interest\":9.15,\"sl_no\":146,\"EDI_amount\":615},{\"principal\":606.76,\"interest\":8.24,\"sl_no\":147,\"EDI_amount\":615},{\"principal\":607.67,\"interest\":7.33,\"sl_no\":148,\"EDI_amount\":615},{\"principal\":608.58,\"interest\":6.42,\"sl_no\":149,\"EDI_amount\":615},{\"principal\":609.49,\"interest\":5.51,\"sl_no\":150,\"EDI_amount\":615},{\"principal\":610.41,\"interest\":4.59,\"sl_no\":151,\"EDI_amount\":615},{\"principal\":611.32,\"interest\":3.68,\"sl_no\":152,\"EDI_amount\":615},{\"principal\":612.24,\"interest\":2.76,\"sl_no\":153,\"EDI_amount\":615},{\"principal\":613.16,\"interest\":1.84,\"sl_no\":154,\"EDI_amount\":615},{\"principal\":614.04,\"interest\":0.96,\"sl_no\":155,\"EDI_amount\":615}]";
//                            Map virtualAccount = apiGatewayService.getVirtualAccountIfsc(merchantId);
                                String accountNumber = ldcVirtualAccount.getAccountNumber().toString();
                                String ifscCode = ldcVirtualAccount.getIfsc().toString();
                                Map addressResult = apiGatewayService.getKycDetails(application.getId(),application.getMerchant().getId());
                                String gender = addressResult.get("gender").toString();
                                String dob = addressResult.get("dob").toString();
                                String proofType = addressResult.get("proof_type").toString();
                                String personName = addressResult.get("person_name").toString();
                                String pancardUrl = addressResult.get("pancardUrl").toString();
                                String addressproof1 = addressResult.get("addressproof1").toString();
                                String addressproof2 = addressResult.get("addressproof2").toString();
                                LendingEkyc lendingEkyc = lendingEkycDao.findSuccessEkyc(application.getMerchant().getId(),application.getId());
                                data.add(new String[] {"AMPLB","PL",application.getLoanAmount().toString(),application.getTenureInMonths().toString(),application.getExternalLoanId(),application.getProcessingFee().toString(),"0", String.valueOf((application.getInterestRate()*12/100)),"flat",application.getDisbursalAmount().toString(), String.valueOf((application.getRepayment()-application.getLoanAmount())),application.getPayableDays().toString(),lendingApplication.getEdi().toString(),ediSchedule,experian.getColor(),apiGatewayService.getPincodeArea(experian.getPincode()),"Y",apiGatewayService.findNtc(experian),"N","Y","Recommended",dob,personName,gender," ",experian.getPancardNumber(),application.getMerchant().getMobile(),"Personal",application.getPincode().toString(),application.getStreetAddress(),application.getCity(),application.getState(),"permanent",proofType,"communication","self owned",application.getLandmark(),"ICICI BANK",accountNumber,application.getMerchant().getBeneficiaryName(),ifscCode,addressproof1,addressproof2,pancardUrl,apiGatewayService.getLoanAgreement(application.getMerchant().getId(),application.getId()),lendingEkyc != null ? lendingEkyc.getResponse() : null});
                            }
                        }else if("HINDON".equalsIgnoreCase(lender)){
                            String[] header = { "PaymentType", "CusRefNumber","SourceAccountNumber","SourceNarration","LoanID","DestinationAccountNumber","Currency","Amount","ProcessingFee","DisbursalAmount","DestinationNarration","Destinationbank","DestinationBankIFSCode","BeneficiaryName","BeneficiaryAccountType" };
                            data.add(header);
                            int i =1;
                            for (LendingBulkDisbursalRawData bulklender : lendingBulkDisbursalRawData) {
                                LendingApplication application = lendingApplicationDao.findByIdAndMerchantId(bulklender.getApplicationId(), bulklender.getMerchantId());
                                MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(application.getMerchant().getId(),"ACTIVE");
                                data.add(new String[] {"NFT",String.valueOf(i),"403040506070","HINDON MERCANTILE LIMITED",application.getExternalLoanId(), "\'"+merchantBankDetail.getAccountNumber(),"INR",application.getLoanAmount().toString(),application.getProcessingFee().toString(),application.getDisbursalAmount().toString(),"AgainstLoan",merchantBankDetail.getBankName(),merchantBankDetail.getIfscCode(),merchantBankDetail.getBeneficiaryName(),merchantBankDetail.getAccType(),""});
                                i++;
                            }
                        }
                        writer.writeAll(data);
                        writer.close();
                        byte[] bytes = Files.readAllBytes(Paths.get("/tmp/"+fileId+"_nbfc_details.csv"));
                        emailHandler.sendEmailWithAttachement(new ArrayList<String>() {{add("rohit.dhola@bharatpe.com");
                        }}, "Automated Nbfc Report", "Nbfc Detaild Report", bytes, "nbfc_details", "text/csv");
                        s3BucketHandler.uploadFileToS3(file,"crm-exporter",fileId+"__nbfc_details.csv");

                        Optional<LendingBulkDisbursal> lendingBulkDisbursal=lendingBulkDisbursalDao.findById(fileId);
                        if(lendingBulkDisbursal.isPresent()){
                            lendingBulkDisbursal.get().setReturnFileName(fileId+"__nbfc_details.csv");
                            lendingBulkDisbursalDao.save(lendingBulkDisbursal.get());
                        }
                    }catch(Exception ex){
                        logger.info("Exception In generating CSV :{}",ex);
                    }
                }
            }).start();
        } catch (Exception ex) {
            logger.info("Exception In Bulk Lender Change :{}", ex);
            SupportLoanResponseDTO supportLoanResponseDTO = new SupportLoanResponseDTO();
            responseDTO.setData(supportLoanResponseDTO);
        }

        return responseDTO;
    }

}
