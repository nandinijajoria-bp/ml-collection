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
import com.bharatpe.lending.constant.ExperianConstants;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.constant.SupportConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingLedgerDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.LoanAgreementDao;
import com.bharatpe.lending.dto.CommonResponse;
import com.bharatpe.lending.dto.SupportLoanResponseDTO;
import com.bharatpe.lending.dto.SupportResponseDTO;
import com.bharatpe.lending.entity.LoanAgreement;
import com.bharatpe.lending.enums.ApplicationStatus;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.html2pdf.HtmlConverter;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    LoanAgreementDao loanAgreementDao;

    @Autowired
    LendingPayoutsDao lendingPayoutsDao;

    @Autowired
    LendingBulkDisbursalDao lendingBulkDisbursalDao;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LoanDpdDao loanDpdDao;

    ExecutorService executorService = Executors.newFixedThreadPool(5);

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

            LendingApplication lendingApplicationNew =
                lendingApplicationDao.findTopByMerchantIdAndLoanDisbursalStatusNullOrderByIdDesc(merchantId);
            EligibleLoan eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            if (ObjectUtils.isEmpty(lendingApplicationNew)) {
                if (ObjectUtils.isEmpty(eligibleLoan)) {
                    logger.info("Eligible loan offer not found for merchantId: {}", merchantId);
                    supportLoanResponseDTO.setApplicationStatus(SupportConstants.NOT_ELIGIBLE);
                    supportLoanResponseDTO.setMessage("NA");
                    supportLoanResponseDTO.setConditionalMessage(SupportConstants.NOT_ELIGIBLE_MESSAGE);
                    supportLoanResponseDTO.setEligible(Boolean.FALSE);
                    responseDTO.setData(supportLoanResponseDTO);
                    return responseDTO;
                }
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

            if(ApplicationStatus.DRAFT.name().equalsIgnoreCase(lendingApplication.getStatus())) {
                logger.info("Application status is in DRAFT for merchantId: {}, and "
                    + "applicationId: {}", merchantId, lendingApplication.getId());
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

                    if ("NTB".equalsIgnoreCase(lendingApplication.getLoanType()) || "NTB_SMS_1".equalsIgnoreCase(lendingApplication.getLoanType())) {
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
                    loanArrangerFee.setInEligibleReason(SupportConstants.ALREADY_REFUNDED);
                } else {
                    populateArrangerFeeEligible(lendingPaymentSchedule1, loanArrangerFee);
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
                loanDetails.put("dpd", getDPD(lendingPaymentSchedule1));
                loanDetails.put("ledgerDetails", lendingLedgerDetailList);
                loanDetails.put("loanArrangerFee", loanArrangerFee);

                loanHistoryList.add(loanDetails);
            }
            supportLoanResponseDTO.setLoanDetailsList(loanHistoryList);
            return supportLoanResponseDTO;

        }
        return supportLoanResponseDTO;
    }

    private void populateArrangerFeeEligible(LendingPaymentSchedule lendingPaymentSchedule,
                                              SupportLoanResponseDTO.LoanArrangerFee loanArrangerFee) {
        if (lendingPaymentSchedule.getStatus().equals("CLOSED") && lendingPaymentSchedule.getLoanApplication() != null && lendingPaymentSchedule.getLoanApplication().getProcessingFee() != null && lendingPaymentSchedule.getLoanApplication().getProcessingFee() > 0D) {
            BigInteger maxDpd = loanDpdDao.findMaxDpd(lendingPaymentSchedule.getId());
            long dpd = LoanUtil.getDateDiffInDays(lendingPaymentSchedule.getTentativeClosingDate(), lendingPaymentSchedule.getClosingDate());
            LendingLedger lendingLedger = lendingLedgerDao.getForClosedLedger(lendingPaymentSchedule.getId());
            Long loanId = lendingLedgerDao.getLedgerByAdjustmentModes(lendingPaymentSchedule.getId());

            if (maxDpd.intValue() <= 5 && dpd <= 5 && (dpd >= -5 || Objects.isNull(lendingLedger)) && ObjectUtils.isEmpty(loanId)) {
                loanArrangerFee.setArrangerFeeRefundEligible(Boolean.TRUE);
            }

            if(maxDpd.intValue() <= 5 && dpd <= 5 && dpd >= -5) {
                loanArrangerFee.setInEligibleReason(SupportConstants.MAX_DPD);
            }

            if(!ObjectUtils.isEmpty(loanId)) {
                loanArrangerFee.setInEligibleReason(SupportConstants.NEW_LOAN_ACTIVE);
            }

            if(!ObjectUtils.isEmpty(lendingLedger)) {
                loanArrangerFee.setInEligibleReason(SupportConstants.FORE_CLOSER_LOAN);
            }
        }

        if(!"CLOSED".equalsIgnoreCase(lendingPaymentSchedule.getStatus())) {
            loanArrangerFee.setInEligibleReason(SupportConstants.LOAN_NOT_CLOSED);
        }

        loanArrangerFee.setArrangerFeeRefundEligible(Boolean.FALSE);
    }

    public SupportResponseDTO bulkLenderchange(Long merchantId, Long applicationId, Long fileId, Boolean flag, String lender) {
        logger.info("Lender Change For Merchant Id:{}, and ApplicationId:{}", merchantId, applicationId);
        SupportResponseDTO responseDTO = new SupportResponseDTO(true, "OK");
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
            if (!"approved".equalsIgnoreCase(lendingApplication.getStatus()) || lendingApplication.getDisburseTimestamp() != null || "YES".equalsIgnoreCase(lendingApplication.getSendToNbfc())) {
                responseDTO.setSuccess(false);
                responseDTO.setMessage("Not Match Condition");
                return responseDTO;
            }
            Optional<LendingBulkDisbursal> lendingBulkDisbursal=lendingBulkDisbursalDao.findById(fileId);
            if(!lendingBulkDisbursal.isPresent() || lendingBulkDisbursal.get().getProceed()){
                responseDTO.setSuccess(false);
                responseDTO.setMessage("Already Proceed");
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
                        logger.info("MAMTA Lender Change:{}",lender);
                        String[] header = { "partner_tag", "loan_type", "Loan_amount","tenure","partner_loan_id","fee_amount","gst_amount","interest_rate","interest_type","partner_computed_disbursement_amount","partner_computed_interest_amount","no_of_EDI","EDI_amount","EDI_schedule","customer_risk_segment","customer_location_category","existing_BP_merchant","customer_type_NTC","any_written_off_loan_in_last_two_years","income_to_debt_ratio","recommendation_from_BP","date_of_birth","consumer_name","gender","email","pan_number","mobile_number","loan_purpose","pincode","address","city","address_state","address_type","address_proof_type","type","stay_type","landmark","customer_bank_name","bank_account_number","customer_bank_account_name","ifsc_code","address_proof_1","address_proof_2","pan_card","loan_agreement","eKycResponse" };
                        data.add(header);
                        for (LendingBulkDisbursalRawData bulklender : lendingBulkDisbursalRawData) {
                            LendingApplication application = lendingApplicationDao.findByIdAndMerchantId(bulklender.getApplicationId(), bulklender.getMerchantId());
                            Experian experian = experianDao.getByMerchantId(bulklender.getMerchantId());
                            CommonResponse ediScheduleResponse = lendingEdiScheduleService.getEdiSchedule(application.getMerchant().getId(), application.getId());
                            String ediSchedule = ediScheduleResponse.getData().toString();
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
                            data.add(new String[] {"AMPLB","PL",application.getLoanAmount().toString(),application.getTenureInMonths().toString(),application.getExternalLoanId(),application.getProcessingFee().toString(),"0", String.valueOf((application.getInterestRate()*12/100)),"flat",application.getDisbursalAmount().toString(), String.valueOf((application.getRepayment()-application.getLoanAmount())),application.getPayableDays().toString(),lendingApplication.getEdi().toString(),ediSchedule,experian.getColor(),apiGatewayService.getPincodeArea(experian.getPincode()),"Y",apiGatewayService.findNtc(experian),"N","Y","Recommended",dob,personName,gender," ",experian.getPancardNumber(),application.getMerchant().getMobile(),"Personal",application.getPincode().toString(),application.getShopNumber()+application.getStreetAddress()+application.getArea()+application.getLandmark(),application.getCity(),application.getState(),"permanent",proofType,"communication","self owned",application.getLandmark(),"ICICI BANK",accountNumber,application.getMerchant().getBeneficiaryName(),ifscCode,addressproof1,addressproof2,pancardUrl,apiGatewayService.getLoanAgreement(application.getMerchant().getId(),application.getId()),lendingEkyc != null ? lendingEkyc.getResponse() : null});
                            bulklender.setStatus("SUCCESS");
                            lendingBulkDisbursalRawDataDao.save(bulklender);
                        }
                        writer.writeAll(data);
                        writer.close();
                        byte[] bytes = Files.readAllBytes(Paths.get("/tmp/"+fileId+"_nbfc_details.csv"));
                        emailHandler.sendEmailWithAttachement(new ArrayList<String>() {{add("rohit.dhola@bharatpe.com");
                        }}, "Automated Nbfc Report", "MAMTA Nbfc Details Report"+new Date(), bytes, "nbfc_details", "text/csv");
                        s3BucketHandler.uploadFileToS3(file,"crm-exporter",fileId+"_nbfc_details.csv");

                        if(lendingBulkDisbursal.isPresent()){
                            lendingBulkDisbursal.get().setReturnFileName(fileId+"_nbfc_details.csv");
                            lendingBulkDisbursal.get().setProceed(Boolean.TRUE);
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

    public SupportResponseDTO changeLender(String lender,Long fileId,Integer lines) {
        Optional<LendingBulkDisbursal> lendingBulkDisbursal=lendingBulkDisbursalDao.findById(fileId);
        SupportResponseDTO responseDTO = new SupportResponseDTO(true, "OK");
        if(!lendingBulkDisbursal.isPresent() || lendingBulkDisbursal.get().getProceed()){
            responseDTO.setSuccess(false);
            responseDTO.setMessage("Already Proceed");
            return responseDTO;
        }
        new Thread(() -> lenderChange(lender, fileId, lines, lendingBulkDisbursal.get())).start();
        return responseDTO;
    }


    public void lenderChange(String lender,Long fileId,Integer lines, LendingBulkDisbursal lendingBulkDisbursal){
        logger.info("Lender Change started For fileName:{}, and lender:{}", fileId, lender);
        try{
            List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
            InputStream lenderFile = s3BucketHandler.getObject(lendingBulkDisbursal.getFileName(), "loan-document");
            BufferedReader lenderFileReader = new BufferedReader(new InputStreamReader(lenderFile));
            File file = new File("/tmp/"+fileId+"_nbfc_details.csv");
            File errorFile = new File("/tmp/"+fileId+"_error_file.csv");
            FileWriter outputfile = new FileWriter(file);
            CSVWriter writer = new CSVWriter(outputfile);
            FileWriter outputError = new FileWriter(errorFile);
            CSVWriter errorWriter = new CSVWriter(outputError);
            List<String[]> data = new ArrayList<String[]>();
            List<String[]> errorData = new ArrayList<String[]>();
            String[] errorheader = {"merchant_id","application_id","external_loan_id","status","message"};
            errorData.add(errorheader);
            String[] header;
//            if("MAMTA".equalsIgnoreCase(lender)){
            header = new String[]{"partner_tag", "loan_type", "Loan_amount", "tenure", "partner_loan_id", "fee_amount", "gst_amount", "interest_rate", "interest_type", "partner_computed_disbursement_amount", "partner_computed_interest_amount", "no_of_EDI", "EDI_amount", "EDI_schedule", "customer_risk_segment", "customer_location_category", "existing_BP_merchant", "customer_type_NTC", "any_written_off_loan_in_last_two_years", "income_to_debt_ratio", "recommendation_from_BP", "date_of_birth", "consumer_name", "gender", "email", "pan_number", "mobile_number", "loan_purpose", "pincode", "address", "city", "address_state", "address_type", "address_proof_type", "type", "stay_type", "landmark", "customer_bank_name", "bank_account_number", "customer_bank_account_name", "ifsc_code", "address_proof_1", "address_proof_2", "pan_card", "loan_agreement", "eKycResponse"};
//            }else{
//                header = new String[]{"PaymentType", "CusRefNumber", "SourceAccountNumber", "SourceNarration", "LoanID", "DestinationAccountNumber", "Currency", "Amount", "ProcessingFee", "DisbursalAmount", "DestinationNarration", "Destinationbank", "DestinationBankIFSCode", "BeneficiaryName", "BeneficiaryAccountType", "Email"};
//            }
            data.add(header);
            CountDownLatch latch = new CountDownLatch(lines);
            String readLine = lenderFileReader.readLine();
            readLine = lenderFileReader.readLine();
            int count = 0;
            while (readLine != null) {
                logger.info("Line:{}",readLine);
                String[] arr = readLine.split(",");
                Long merchantId = Long.valueOf(arr[1].replaceAll("^\"|\"$", ""));
                Long applicationId = Long.valueOf(arr[2].replaceAll("^\"|\"$", ""));
                LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId,merchantId);


                if(lendingApplication == null){
                    logger.info("Application Not Found merchantId:{} and applicationId:{}",merchantId,applicationId);
                    errorData.add(new String[]{merchantId.toString(),applicationId.toString(),arr[3],"FAILED","Application not Found"});
                    readLine = lenderFileReader.readLine();
                    latch.countDown();
                    continue;
                }

//                if (!topupLoans.contains(lendingApplication.getLoanType()) && lendingApplication.getLoanType().equalsIgnoreCase(LoanType.NTB.toString()) && lendingApplication.getMerchant().getBusinessCategory() != null && !LendingConstants.ESSENTIAL_CATEGORIES.contains(lendingApplication.getMerchant().getBusinessCategory())) {
//                    logger.info("Merchant Category not Match for merchantId:{} and applicationId:{}",merchantId,applicationId);
//                    errorData.add(new String[]{merchantId.toString(),applicationId.toString(),lendingApplication.getExternalLoanId(),"FAILED","Merchant Category Not match"});
//                    readLine = lenderFileReader.readLine();
//                    latch.countDown();
//                    continue;
//                }
                if(!"approved".equalsIgnoreCase(lendingApplication.getStatus()) || lendingApplication.getDisburseTimestamp() != null || "YES".equalsIgnoreCase(lendingApplication.getSendToNbfc())){
                    logger.info("Application Condition Not Match merchantId:{} and applicationId:{}",merchantId,applicationId);
                    errorData.add(new String[]{lendingApplication.getMerchant().getId().toString(),lendingApplication.getId().toString(),lendingApplication.getExternalLoanId(),"FAILED","Condition Not Match"});
                    readLine = lenderFileReader.readLine();
                    latch.countDown();
                    continue;
                }

//                if("NTB".equalsIgnoreCase(lendingApplication.getLoanType()) && repeatLoan == 0 ){
//                    logger.info("Application Do not Disburse for  merchantId:{} and applicationId:{}",merchantId,applicationId);
//                    errorData.add(new String[]{lendingApplication.getMerchant().getId().toString(),lendingApplication.getId().toString(),lendingApplication.getExternalLoanId(),"FAILED","Application Do not Disburse"});
//                    readLine = lenderFileReader.readLine();
//                    latch.countDown();
//                    continue;
//                }

                LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId,"ACTIVE");
                if(lendingPaymentSchedule != null){
                    logger.info("Merchant Have a Active Loan For merchantId:{}",merchantId);
                    errorData.add(new String[]{lendingApplication.getMerchant().getId().toString(),lendingApplication.getId().toString(),lendingApplication.getExternalLoanId(),"FAILED","Merchant Has Active Loan"});
                    lendingApplication.setStatus("deleted");
                    lendingApplication.setResponseCode("Duplicate Disbursal");
                    lendingApplication.setAgreement(0);
                    lendingApplicationDao.save(lendingApplication);
                    executorService.execute(() -> apiGatewayService.globalLimitTxn(lendingApplication.getMerchant().getId(), "CREDIT",lendingApplication.getLoanAmount()));
                    readLine = lenderFileReader.readLine();
                    latch.countDown();
                    continue;
                }
                LendingApplication pendingDisbusal = lendingApplicationDao.findPendingDisbursal(merchantId);
                if(pendingDisbusal != null){
                    logger.info("Application Already Pending Disbursal For merchantId:{}",merchantId);
                    errorData.add(new String[]{lendingApplication.getMerchant().getId().toString(),lendingApplication.getId().toString(),lendingApplication.getExternalLoanId(),"FAILED","Merchant Another Application Already Sent For Disbursal"});
                    lendingApplication.setStatus("deleted");
                    lendingApplication.setResponseCode("Duplicate Disbursal");
                    lendingApplication.setAgreement(0);
                    lendingApplicationDao.save(lendingApplication);
                    executorService.execute(() -> apiGatewayService.globalLimitTxn(lendingApplication.getMerchant().getId(), "CREDIT",lendingApplication.getLoanAmount()));
                    readLine = lenderFileReader.readLine();
                    latch.countDown();
                    continue;
                }
                Experian experian = experianDao.getByMerchantId(lendingApplication.getMerchant().getId());
                if(!topupLoans.contains(lendingApplication.getLoanType())){
                    if("RED".equalsIgnoreCase(experian.getColor())){
                        logger.info("Application CIBIL Is RED merchantId:{} and applicationId:{}",merchantId,applicationId);
                        errorData.add(new String[]{lendingApplication.getMerchant().getId().toString(),lendingApplication.getId().toString(),lendingApplication.getExternalLoanId(),"FAILED","CIBIL RED"});
                        readLine = lenderFileReader.readLine();
                        latch.countDown();
                        continue;
                    }
                }
                try {
                    data.add(getCsvData(lendingApplication,lender,experian));
                    if(!"YES".equalsIgnoreCase(lendingApplication.getSendToNbfc())){
                        errorData.add(new String[]{lendingApplication.getMerchant().getId().toString(),lendingApplication.getId().toString(),lendingApplication.getExternalLoanId(),"FAILED","POA Details Not Correct"});
                    }
                } catch (IOException e) {
                    errorData.add(new String[]{lendingApplication.getMerchant().getId().toString(),lendingApplication.getId().toString(),lendingApplication.getExternalLoanId(),"FAILED","Some Details Missing!"});
                    logger.error("Exception while writing csv data in lender change for application:{}", lendingApplication.getId(), e);
                } finally {
                    latch.countDown();
                }
                readLine=lenderFileReader.readLine() ;
                count++;
            }
            logger.info("total lender change:{}", count);
            if (latch.getCount() > 0) {
                logger.error("lender change latch is not 0 for file:{}", fileId);
            }
            lenderFileReader.close();
            lenderFile.close();
            latch.await();
            errorWriter.writeAll(errorData);
            errorWriter.close();
            writer.writeAll(data);
            writer.close();
            outputError.close();
            outputfile.close();
            byte[] bytes = Files.readAllBytes(Paths.get("/tmp/"+fileId+"_nbfc_details.csv"));
            byte[] error = Files.readAllBytes(Paths.get("/tmp/"+fileId+"_error_file.csv"));
            List<String> hindonEmails = Arrays.asList("rohit.dhola@bharatpe.com","sandeep.chauhan@bharatpe.com","anuj.puri@bharatpe.com","ashutosh.dhewal@bharatpe.com","kanika.sehgal@bharatpe.com","accounts@bharatpe.com","Helpdesk@mufinfinance.com","Rajat@mufinfinance.com","rajat.jain@bharatpe.com","anik.kansal@bharatpe.com","khushal.virmani@bharatpe.com"," psabharwal@mufinfinance.com","lending_ops_reports@bharatpe.com");
            List<String> mamtaEmails = Arrays.asList("rohit.dhola@bharatpe.com","sandeep.chauhan@bharatpe.com","anuj.puri@bharatpe.com","ashutosh.dhewal@bharatpe.com","kanika.sehgal@bharatpe.com","anik.kansal@bharatpe.com","khushal.virmani@bharatpe.com","ashwani.dograext@bharatpe.com","gaurav.parashar@bharatpe.com"," lending_ops_reports@bharatpe.com");
            emailHandler.sendEmailWithAttachement("HINDON".equalsIgnoreCase(lender) ? hindonEmails : mamtaEmails, "Customer Onboarding and Loan Approval: "+ lender +"  "+new Date(), "Customer Onboarding and Loan Approval For :"+lender+" "+new Date() , bytes, lender+"_nbfc_details.csv", "text/csv");
            emailHandler.sendEmailWithAttachement(new ArrayList<String>() {{add("rohit.dhola@bharatpe.com") ; add("lending_ops_reports@bharatpe.com");
            }}, lender+" NBFC Error Cases Report  "+new Date(), lender+" NBFC Error Cases For Date "+new Date() , error, lender+"_error_cases.csv", "text/csv");
            s3BucketHandler.uploadFileToS3(file,"crm-exporter",fileId+"_nbfc_details.csv");
            s3BucketHandler.uploadFileToS3(errorFile,"crm-exporter",fileId+"_error_file.csv");
            lendingBulkDisbursal.setReturnFileName(fileId+"_nbfc_details.csv");

            lendingBulkDisbursal.setProceed(Boolean.TRUE);
            lendingBulkDisbursalDao.save(lendingBulkDisbursal);

            file.delete();
            errorFile.delete();
            bytes = null;
            error = null;

        }catch(Exception ex){
            logger.error("Exception IN Lender Change for file:{}", fileId,ex);
        }
        logger.info("Lender Change completed For fileName:{}, and lender:{}", fileId, lender);
    }

    private String[] getCsvData(LendingApplication lendingApplication, String lender,Experian experian) throws IOException {
        List<String> topupLoans = Arrays.asList(LoanType.TOPUP.name(), LoanType.HALF_TOPUP.name(), LoanType.IO_TOPUP.name());
        String shortUrl = getAgreement(lendingApplication,lender);
        LdcVirtualAccount ldcVirtualAccount= apiGatewayService.createDisbursalVPA(lendingApplication.getMerchant(),lendingApplication);
        CommonResponse ediScheduleResponse = lendingEdiScheduleService.getEdiSchedule(lendingApplication.getMerchant().getId(), lendingApplication.getId());
        String ediSchedule = objectMapper.writeValueAsString(ediScheduleResponse.getData());
        String accountNumber = ldcVirtualAccount.getAccountNumber();
        String ifscCode = ldcVirtualAccount.getIfsc();
        Map addressResult = apiGatewayService.getKycDetails(lendingApplication.getId(),lendingApplication.getMerchant().getId());
        String gender = addressResult.get("gender").toString();
        String dob = addressResult.get("dob").toString();
        String proofType = addressResult.get("proof_type").toString();
        String personName = addressResult.get("person_name").toString();
        String pancardUrl = addressResult.get("pancardUrl").toString();
        String addressproof1 = addressResult.get("addressproof1").toString();
        String addressproof2 = addressResult.get("addressproof2").toString();
        LendingEkyc lendingEkyc = lendingEkycDao.findSuccessEkyc(lendingApplication.getMerchant().getId(),lendingApplication.getId());
        int ediDays = lendingApplication.getPayableDays().intValue();
        if (lendingApplication.getIoPayableDays() != null) {
            ediDays += lendingApplication.getIoPayableDays();
        }
        String[] data;
        String accType = lender.equals("LDC") ? "INVESTOR_FUNDS" : "NBFC_FUNDS";
        String riskColor = topupLoans.contains(lendingApplication.getLoanType()) ? ExperianConstants.COLOR.LIGHT_GREEN.name() : experian.getColor();
        Double disbursalAmount = topupLoans.contains(lendingApplication.getLoanType()) ? lendingApplication.getLoanAmount() : lendingApplication.getDisbursalAmount();
        String location = topupLoans.contains(lendingApplication.getLoanType()) ? "GREEN" : apiGatewayService.getPincodeArea(experian.getPincode());
        if("MAMTA".equalsIgnoreCase(lender)){
            data = new String[]{"AMPLB", "PL", lendingApplication.getLoanAmount().toString(), lendingApplication.getTenureInMonths().toString(), lendingApplication.getExternalLoanId(), lendingApplication.getProcessingFee().toString(), "0", String.valueOf((lendingApplication.getInterestRate() * 12 / 100)), "flat", String.valueOf(disbursalAmount), String.valueOf((lendingApplication.getRepayment() - lendingApplication.getLoanAmount())), lendingApplication.getPayableDays().toString(), lendingApplication.getEdi().toString(), ediSchedule, riskColor, location, "Y", apiGatewayService.findNtc(experian), "N", "Y", "Recommended", dob, personName, gender, " ", experian.getPancardNumber(), lendingApplication.getMerchant().getMobile(), "Personal", lendingApplication.getPincode().toString(), lendingApplication.getShopNumber() + lendingApplication.getStreetAddress() + lendingApplication.getArea() + lendingApplication.getLandmark(), lendingApplication.getCity(), lendingApplication.getState(), "permanent", proofType, "communication", "self owned", lendingApplication.getLandmark(), "ICICI BANK", "\'" + accountNumber, lendingApplication.getMerchant().getBeneficiaryName(), ifscCode, addressproof1, addressproof2, pancardUrl, shortUrl, lendingEkyc != null ? lendingEkyc.getResponse() : null};
        }else{
            data = new String[]{"HINDON", "PL", lendingApplication.getLoanAmount().toString(), lendingApplication.getTenureInMonths().toString(), lendingApplication.getExternalLoanId(), lendingApplication.getProcessingFee().toString(), "0", String.valueOf((lendingApplication.getInterestRate() * 12 / 100)), "flat", String.valueOf(disbursalAmount), String.valueOf((lendingApplication.getRepayment() - lendingApplication.getLoanAmount())), String.valueOf(ediDays), lendingApplication.getEdi().toString(), ediSchedule, riskColor, location, "Y", apiGatewayService.findNtc(experian), "N", "Y", "Recommended", dob, personName, gender, " ", experian.getPancardNumber(), lendingApplication.getMerchant().getMobile(), "Personal", lendingApplication.getPincode().toString(), lendingApplication.getShopNumber() + lendingApplication.getStreetAddress() + lendingApplication.getArea() + lendingApplication.getLandmark(), lendingApplication.getCity(), lendingApplication.getState(), "permanent", proofType, "communication", "self owned", lendingApplication.getLandmark(), "ICICI BANK", "\'" + accountNumber, lendingApplication.getMerchant().getBeneficiaryName(), ifscCode, addressproof1, addressproof2, pancardUrl, shortUrl, lendingEkyc != null ? lendingEkyc.getResponse() : null};
        }
        lendingApplication.setLender(lender);
        lendingApplication.setAccountType(accType);
        lendingApplication.setSendToNbfc("YES");
        lendingApplication.setNbfcSendDate(new Date());
        lendingApplication.setLoanDisbursalStatus("PENDING");
        lendingApplication.setDisbursalPartner("BHARATPE");
        lendingApplicationDao.save(lendingApplication);
        return data;

    }

    public String getAgreement(LendingApplication lendingApplication,String lender) throws IOException {
        Map<String,Object> data = new HashMap<>();
        MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(lendingApplication.getMerchant().getId(),"ACTIVE");
        Experian experian = experianDao.getByMerchantId(lendingApplication.getMerchant().getId());
        SimpleDateFormat date = new SimpleDateFormat("DD-MMM-YYYY");
        data.put("externalLoanId",lendingApplication.getExternalLoanId());
        data.put("loanAmount",lendingApplication.getLoanAmount());
        data.put("tenure",lendingApplication.getTenure());
        data.put("interestRate",lendingApplication.getInterestRate());
        data.put("interestRateAnnum",lendingApplication.getInterestRate()*12);
        data.put("edi",lendingApplication.getEdi());
        data.put("processingFee",lendingApplication.getProcessingFee());
        data.put("mobile",lendingApplication.getMerchant().getMobile());
        data.put("location",lendingApplication.getCity());
        data.put("shopNumber",lendingApplication.getShopNumber());
        data.put("streetAddress",lendingApplication.getStreetAddress());
        data.put("landmark",lendingApplication.getLandmark());
        data.put("area",lendingApplication.getArea());
        data.put("pincode",lendingApplication.getPincode());
        data.put("city",lendingApplication.getCity());
        data.put("state",lendingApplication.getState());
        data.put("email",lendingApplication.getEmail());
        data.put("createdAt",new SimpleDateFormat("dd-MMM-yyyy").format(lendingApplication.getCreatedAt()));
        data.put("agreementAt",new SimpleDateFormat("dd-MMM-yyyy").format(lendingApplication.getAgreementAt()));
        data.put("merchantName",lendingApplication.getMerchant().getBeneficiaryName());
        data.put("payableDays",lendingApplication.getPayableDays());
        data.put("businessCategory",lendingApplication.getCategory());
        data.put("browserName",lendingApplication.getLoanAmount());
        data.put("ipAddress",lendingApplication.getIp());
        data.put("accountNumber",merchantBankDetail.getAccountNumber());
        data.put("ifsc",merchantBankDetail.getIfscCode());
        data.put("bankName",merchantBankDetail.getBankName());
        data.put("beneficiaryName",lendingApplication.getMerchant().getBeneficiaryName());
        data.put("repayment",lendingApplication.getRepayment());
        data.put("panNumber",experian.getPancardNumber());
        data.put("lenderName", lendingApplication.getLender());

        String html = getAgreementHtml(data,lender);
        String shortUrl = storeAgreement(lendingApplication,html,"agreement","LoanAgreement_" + lendingApplication.getMerchant().getId() + "_" + lendingApplication.getId() + ".pdf");

        String welcomeLetter = getWelcomeLetterHtml(data);
        String welcomeLetterUrl = storeAgreement(lendingApplication,welcomeLetter,"welcome","Welcome_Letter_"+ lendingApplication.getMerchant().getId() + "_" + lendingApplication.getId() + ".pdf");

        return shortUrl;
    }

    public String storeAgreement(LendingApplication lendingApplication,String html,String type,String fileName) throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        HtmlConverter.convertToPdf(html, outStream);
        ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
        s3BucketHandler.uploadToS3PdfBucket(inStream, fileName, "bharatpe-agreement");
        LoanAgreement loanAgreement = loanAgreementDao.findByApplicationIdAndType(lendingApplication.getId(),type);
        if(loanAgreement == null){
            loanAgreement = new LoanAgreement();
            loanAgreement.setMerchantId(lendingApplication.getMerchant().getId());
            loanAgreement.setApplicationId(lendingApplication.getId());
        }
        loanAgreement.setType(type);
        loanAgreement.setAgreementName(fileName);
        loanAgreementDao.save(loanAgreement);
        String shortUrl = "";
        try {
            shortUrl = getShorturl(fileName, loanAgreement);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return  shortUrl;
    }

    public String getShorturl(String fileName, LoanAgreement loanAgreement) throws UnsupportedEncodingException {
        String tempUrl="";
        try {
            tempUrl=s3BucketHandler.getPreSignedPublicURL(fileName, "bharatpe-agreement");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        String url = "https://bharatpe.in/yourls-api.php?signature=a872b1348e&action=shorturl&format=json&keyword=&url="+ URLEncoder.encode(tempUrl,"UTF-8");
        String response="";
        try {
            Instant start = Instant.now();
            response = restTemplate.getForObject(url,String.class);
            logger.info("shorturl response : {}", response);
            Instant end = Instant.now();
            logger.info("Time Taken by shorturl API : {} miliseconds", Duration.between(start, end).toMillis());
        }catch(Exception e) {
            logger.error("exception while shorturl API : {}, Exception is {}", url, e);
        }
        JsonNode rootNode=null;
        try {
            rootNode = objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("Exception while parsing short url---", e);
        }
        if(rootNode != null && rootNode.path("status") != null && rootNode.path("status").textValue().equals("success")){
            String shortUrl=rootNode.path("shorturl").textValue();
            loanAgreement.setShortUrl(shortUrl);
            loanAgreementDao.save(loanAgreement);
            return shortUrl;
        }
        return " ";
    }

    private String getWelcomeLetterHtml(Map<String,Object> data){
        String html = "<p style=\"text-align: center;\"><strong><span style=\"text-decoration: underline;\">WELCOME LETTER</span></strong></p>\n" +
                "<br/>\n" +
                "<p><strong>Date:"+new Date()+"</strong></p>\n" +
                "<br/>\n" +
                "<p><span style=\"font-weight: 400;\">To</span></p>\n" +
                "<p><span style=\"font-weight: 400;\">Name:"+data.get("merchantName")+"</span></p>\n" +
                "<p><span style=\"font-weight: 400;\">Address:"+ data.get("shopNumber") + data.get("streetAddress") + data.get("landmark") + data.get("area") +"</span></p>\n" +
                "<p><span style=\"font-weight: 400;\">Phone:"+data.get("mobile")+"</span></p>\n" +
                "<p><span style=\"font-weight: 400;\">Dear Customer,&nbsp;</span></p>\n" +
                "<p><strong>Welcome to to BharatPe Easy Loan Program</strong></p>\n" +
                "<p><span style=\"font-weight: 400;\">Pursuant to your Loan Application and documents submitted to us, we are pleased to inform that your loan for amount Rs. <span style=\"text-decoration: underline;\">"+data.get("loanAmount")+"</span> (Rupees <span style=\"text-decoration: underline;\">"+data.get("loanAmountWords")+"</span>) has been disbursed to your account no. <span style=\"text-decoration: underline;\">"+data.get("accountNumber")+"</span>. Please find your loan details mentioned below.</span></p>\n" +
                "<br/>\n" +
                "<p><span style=\"font-weight: 400;\">Loan Account No.: <span style=\"text-decoration: underline;\">"+data.get("externalLoanId")+"</span></span></p>\n" +
                "<p><span style=\"font-weight: 400;\">Loan Amount: <span style=\"text-decoration: underline;\">"+data.get("loanAmount")+"</span></span></p>\n" +
                "<p><span style=\"font-weight: 400;\">Tenor: <span style=\"text-decoration: underline;\">"+data.get("tenure")+"</span></span></p>\n" +
                "<p><span style=\"font-weight: 400;\">EDI amount: <span style=\"text-decoration: underline;\">"+data.get("edi")+"</span></span></p>\n" +
                "<p><span style=\"font-weight: 400;\">EDI Frequency: <span style=\"text-decoration: underline;\">Daily - "+data.get("payableDays")+"</span></span></p>\n" +
                "<br/>\n" +
                "<p><span style=\"font-weight: 400;\">For any further details regarding your loan, please go to the 'Need Help?' section on your BharatPe app.</span></p>\n" +
                "<p><span style=\"font-weight: 400;\">&nbsp;</span></p>\n" +
                "<p><strong>Best Regards</strong></p>\n" +
                "<p><strong>"+data.get("lenderName")+"</strong></p>\n" +
                "<br/>\n" +
                "<p><span style=\"font-weight: 400;\">(This is a computer-generated letter hence does not require any signature)</span></p>\n" +
                "<img src=\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxMjAuODQgMzMuMDciPjxkZWZzPjxzdHlsZT4uY2xzLTF7ZmlsbDojZjM3MTYwO30uY2xzLTJ7ZmlsbDojMDA3Yjc4O30uY2xzLTN7ZmlsbDojMDBiN2NlO30uY2xzLTR7ZmlsbDojMjMxZjIwO308L3N0eWxlPjwvZGVmcz48dGl0bGU+QXNzZXQgM0JoYXJhdFBlPC90aXRsZT48ZyBpZD0iTGF5ZXJfMiIgZGF0YS1uYW1lPSJMYXllciAyIj48ZyBpZD0iTGF5ZXJfMS0yIiBkYXRhLW5hbWU9IkxheWVyIDEiPjxwb2x5Z29uIGNsYXNzPSJjbHMtMSIgcG9pbnRzPSIyNC4wNSAxMi44MyA5LjAyIDE2Ljg2IDkuMDIgMTMuMSAyNC4wNSA5LjA3IDI0LjA1IDEyLjgzIi8+PHBvbHlnb24gY2xhc3M9ImNscy0yIiBwb2ludHM9IjI0LjA1IDE5Ljk3IDkuMDIgMjQgOS4wMiAyMC4yNCAyNC4wNSAxNi4yMSAyNC4wNSAxOS45NyIvPjxwYXRoIGNsYXNzPSJjbHMtMyIgZD0iTTE2LjUzLDMzLjA3QTE2LjU0LDE2LjU0LDAsMSwxLDMzLjA3LDE2LjUzLDE2LjU2LDE2LjU2LDAsMCwxLDE2LjUzLDMzLjA3Wm0wLTI5LjY5QTEzLjE2LDEzLjE2LDAsMSwwLDI5LjY5LDE2LjUzLDEzLjE3LDEzLjE3LDAsMCwwLDE2LjUzLDMuMzhaIi8+PHBhdGggY2xhc3M9ImNscy00IiBkPSJNNDcuNzcsMjIuMzVjLTEuNDQsMS4yMy0yLjc2LDEuMjEtNC41OCwxLjIxSDM4Ljc0di0xNGg0LjMyYzEuNjgsMCwzLjI3LDAsNC40OSwxLjM2YTMuMzUsMy4zNSwwLDAsMSwuODMsMi4zMUEzLjE1LDMuMTUsMCwwLDEsNDYuNzUsMTZhMy4yLDMuMiwwLDAsMSwyLjMxLDMuMjZBNCw0LDAsMCwxLDQ3Ljc3LDIyLjM1Wm0tNC4yMi05LjY2SDQyLjE3djIuMThoMS4zNmMuNzgsMCwxLjQyLS4yMywxLjQyLTEuMTJTNDQuMjksMTIuNjksNDMuNTUsMTIuNjlaTTQ0LDE3Ljg2aC0xLjh2Mi41Nkg0NGMuODksMCwxLjY4LS4zMSwxLjY4LTEuMzVTNDQuOCwxNy44Niw0NCwxNy44NloiLz48cGF0aCBjbGFzcz0iY2xzLTQiIGQ9Ik01Ni41LDIzLjU2VjE3Ljg0YzAtMS4xMy0uMzItMi4xOS0xLjY4LTIuMTlzLTEuNzYuODktMS43NiwyLjA4djUuODNINDkuOTF2LTE0aDMuMTVWMTRoMGEyLjk0LDIuOTQsMCwwLDEsMi43MS0xLjMzLDMuODIsMy44MiwwLDAsMSwyLjg2LDEuMTZjMSwxLjE1LDEsMi40LDEsMy44NHY1Ljg3WiIvPjxwYXRoIGNsYXNzPSJjbHMtNCIgZD0iTTY4LjgzLDIzLjU2VjIyLjQyaDBhMy40MiwzLjQyLDAsMCwxLTMuMDcsMS41Yy0zLjIzLDAtNS4yOC0yLjUtNS4yOC01LjYyYTUuMzIsNS4zMiwwLDAsMSw1LjI4LTUuNjEsMy41MiwzLjUyLDAsMCwxLDMuMDcsMS40OGgwVjEzLjA1SDcyVjIzLjU2Wm0tMi42NS03LjkzYTIuNTksMi41OSwwLDAsMC0yLjYsMi43MSwyLjYzLDIuNjMsMCwxLDAsNS4yNSwwQTIuNTgsMi41OCwwLDAsMCw2Ni4xOCwxNS42M1oiLz48cGF0aCBjbGFzcz0iY2xzLTQiIGQ9Ik03Ni40NCwxNy43OXY1Ljc3SDczLjNWMTMuMDVoM3YxLjEyaDBhMi42NCwyLjY0LDAsMCwxLDIuNjMtMS40OHYzLjE2Qzc3LjU0LDE1Ljg5LDc2LjQ0LDE2LjE2LDc2LjQ0LDE3Ljc5WiIvPjxwYXRoIGNsYXNzPSJjbHMtNCIgZD0iTTg3LjQ2LDIzLjU2VjIyLjQyaDBhMy40LDMuNCwwLDAsMS0zLjA2LDEuNWMtMy4yNCwwLTUuMjktMi41LTUuMjktNS42MmE1LjMyLDUuMzIsMCwwLDEsNS4yOS01LjYxLDMuNTIsMy41MiwwLDAsMSwzLjA2LDEuNDhoMFYxMy4wNUg5MC42VjIzLjU2Wm0tMi42NS03LjkzYTIuNTksMi41OSwwLDAsMC0yLjU5LDIuNzEsMi42MiwyLjYyLDAsMSwwLDUuMjQsMEEyLjU4LDIuNTgsMCwwLDAsODQuODEsMTUuNjNaIi8+PHBhdGggY2xhc3M9ImNscy00IiBkPSJNOTYuMjgsMTUuNDR2OC4xMkg5My4xNFYxNS40NEg5MS41OVYxMy4wNWgxLjU1VjkuNTVoMy4xNHYzLjVIOTcuOHYyLjM5WiIvPjxwYXRoIGNsYXNzPSJjbHMtMyIgZD0iTTEwOC4zNiwxOGE1LjYyLDUuNjIsMCwwLDEtNC4wOSwxLjIxaC0xLjU1djQuMzlIOTkuM3YtMTRoNC41OGMxLjQ0LDAsMy4xNi4wNiw0LjMxLDFhNC43Niw0Ljc2LDAsMCwxLDEuNjUsMy43MUE1LjA4LDUuMDgsMCwwLDEsMTA4LjM2LDE4Wm0tNC4zMS01LjI3aC0xLjMzVjE2aDEuNDZjMS4xNywwLDIuMjMtLjI3LDIuMjMtMS42OVMxMDUuMjIsMTIuNjksMTA0LjA1LDEyLjY5WiIvPjxwYXRoIGNsYXNzPSJjbHMtMyIgZD0iTTEyMC43NywxOS4yNmgtNy44OGEyLjM2LDIuMzYsMCwwLDAsMi40NiwyLDIuMjYsMi4yNiwwLDAsMCwxLjkxLTFoMy4yMmE1LjYzLDUuNjMsMCwwLDEtNS4xMywzLjYzLDUuNjEsNS42MSwwLDEsMSw1LjQ5LTUuNDdBNC42NSw0LjY1LDAsMCwxLDEyMC43NywxOS4yNlptLTUuNDYtMy45MmEyLjMzLDIuMzMsMCwwLDAtMi4zOCwxLjhoNC43N0EyLjM0LDIuMzQsMCwwLDAsMTE1LjMxLDE1LjM0WiIvPjwvZz48L2c+PC9zdmc+\" alt=\"Avatar\" style=\"max-width:100%;\">";

        return html;
    }

    public String getAgreementHtml(Map<String,Object> data,String lender){
        String html = "";
        if("MAMTA".equals(lender)) {
             html = "<p style=\"text-align: center;\"><strong>Loan Details</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Loan ID: " + data.get("externalLoanId") + "</span>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span style=\"font-weight: 400;\">Date:" + data.get("agreementAt") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Loan Amount (INR):&nbsp; " + data.get("loanAmount") + "</span> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span style=\"font-weight: 400;\">Tenure (Months):&nbsp; &nbsp; " + data.get("tenure") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Flat Rate of Interest&nbsp;(% per month) : &nbsp;&nbsp;" + data.get("interestRate") + " %</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Flat Rate of Interest&nbsp; (% per annum) :&nbsp;&nbsp;" + data.get("interestRateAnnum") + " %</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Amount of EDI : &nbsp; " + data.get("edi") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Processing fees by BharatPe, if any &nbsp;&nbsp;" + data.get("processingFee") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">BharatPe Registered Mobile Number: " + data.get("mobile") + "</span> <span style=\"font-weight: 400;\"></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Location: " + data.get("location") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">EDI Due Date - Every day from Monday to Saturday from the successive day of disbursal&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Shop/Business Address: " + data.get("shopNumber") + data.get("streetAddress") + data.get("landmark") + data.get("area") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Landmark: " + data.get("landmark") + " &nbsp;&nbsp;&nbsp; PIN: " + data.get("pincode") + " &nbsp;&nbsp;&nbsp;City: " + data.get("city") + " &nbsp;&nbsp;&nbsp; State: " + data.get("state") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Email: " + data.get("email") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Shop/ Business Phone Number: " + data.get("mobile") + "</span></p>\n" +
                    "<h2 style=\"text-align: left;\"><strong>Declaration / Undertaking/Representation by Borrower (MITC)</strong></h2>\n" +
                    "<p><span style=\"font-weight: 400;\">1. I/We hereby apply for a finance facility as proposition made by </span><strong>Resilient Innovation Private Limited (&ldquo;BharatPe&rdquo;)</strong><span style=\"font-weight: 400;\"> as in terms of Loan Agreement as below and declare that all the particulars, information and details provided and other documents submitted by me/us are true, correct, complete and up-to-date in all respects and that I/We have not withheld any material information.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">2. I/We hereby authorize Lender/BharatPe to exchange or share information and details relating to my application to its group companies or any third party, as may be required or deemed fit, for the purpose of processing this loan application and/or related offerings or other products / services that I/We may apply for from time to time.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">3. By submitting this application, I/We hereby expressly authorize Lender/BharatPe to send me communications regarding loans, insurance and other products from Lender/BharatPe, its group companies and / or third parties through telephone calls / SMSs / emails / post etc. including but not limited to promotional, transactional communications. I/We confirm that I shall not challenge receipt of such communications by me as unsolicited communication, defined under TRAI Regulations on Unsolicited Commercial Communications.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">4. I authorize BharatPe / Lender to evaluate my transaction history on the BharatPe platform in order to check my eligibility for the loan and understand and acknowledge that Lender/BharatPe has the absolute discretion, without assigning any reasons to reject my application and that Lender/BharatPe is not answerable / liable to me, in any manner whatsoever, for rejecting my application.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.I / We agrees and accept that Lender/BharatPe may in its sole discretion, by its self or through authorised persons, advocate, agencies, bureau, etc. verify any information given, check credit references, employment details and obtain credit reports to determine creditworthiness from time to time.</span></p>\n" +
                    "<p><strong>LOAN AGREEMENT</strong></p>\n" +
                    "<br/>\n" +
                    "<p><span style=\"font-weight: 400;\">This </span><strong>Loan Agreement</strong><span style=\"font-weight: 400;\"> (&ldquo;</span><strong>Agreement</strong><span style=\"font-weight: 400;\">&rdquo;) is made and executed at the place mentioned in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) and on the date mentioned in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) by and between:</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">MAMTA PROJECTS PRIVATE LIMITED, a non-banking finance company, having its registered office at Room No 1528, 15th Floor,Bengal Intelligent Eco EM-3, Sector-V, Salt Lake City Kolkata 700091 (hereinafter referred to as the &ldquo;</span><strong>Lender</strong><span style=\"font-weight: 400;\">&rdquo; which expression shall, unless repugnant to the context or meaning thereof, be deemed to mean and include its successor(s) and permitted assign(s)) of the One Part;</span></p>\n" +
                    "<p><strong>AND</strong></p>\n" +
                    "<p><strong><em>[Details from the Schedule I]</em></strong><span style=\"font-weight: 400;\">,</span> <span style=\"font-weight: 400;\">hereto as the borrower and co-borrower (if any) (wherever the context so requires) (hereinafter referred to as the &ldquo;</span><strong>Borrower</strong><span style=\"font-weight: 400;\">&rdquo; which expression shall, unless repugnant to the context or meaning thereof, be deemed to mean and include his/her/their heir(s), successor(s), legal representative(s), executor(s), administrator(s) and permitted assign(s)) of the Other Part.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Lender and the Borrower are hereinafter collectively referred to as the &ldquo;</span><strong>Parties</strong><span style=\"font-weight: 400;\">&rdquo; and each individually as the &ldquo;</span><strong>Party</strong><span style=\"font-weight: 400;\">&rdquo;.</span></p>\n" +
                    "<p><strong>WHEREAS</strong><span style=\"font-weight: 400;\">:</span></p>\n" +
                    "<ol>\n" +
                    "<li style=\"font-weight: 400;\"><span style=\"font-weight: 400;\">The Lender is a non-banking financing company, registered with the Reserve Bank of India, having registration no. B.05.05070, and is </span><em><span style=\"font-weight: 400;\">inter alia</span></em><span style=\"font-weight: 400;\"> engaged in the business of advancing loans and other financial facilities.</span></li>\n" +
                    "<li style=\"font-weight: 400;\"><span style=\"font-weight: 400;\">The Borrower has approached the Lender and has requested for grant of loan facility for the purpose of </span><strong><em>as mentioned in Schedule I </em></strong><span style=\"font-weight: 400;\">and in reliance on the acceptance of the terms, conditions, assurances, representations and warranties of the Borrower, the Lender has agreed to grant loan facility to the Borrower, subject to the terms and conditions contained in this Agreement.</span></li>\n" +
                    "<li style=\"font-weight: 400;\"><span style=\"font-weight: 400;\">The Parties hereto are now desirous of </span><em><span style=\"font-weight: 400;\">inter alia</span></em><span style=\"font-weight: 400;\"> entering into this Agreement to set out the terms and conditions in relation to the Facility.</span></li>\n" +
                    "</ol>\n" +
                    "<p><strong>Now, therefore, in view of the foregoing and in consideration of the mutual covenants and agreements herein set forth, the parties hereby agree as follows:</strong></p>\n" +
                    "<br/>\n" +
                    "<p><strong>1.DEFINITIONS AND INTERPRETATION</strong></p>\n" +
                    "<p><strong>1.1 Definitions</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Borrower Account</strong><span style=\"font-weight: 400;\">&rdquo; means the following bank account of the Borrower </span><strong><em>as mentioned in Schedule I</em></strong><span style=\"font-weight: 400;\">, unless otherwise notified by the Borrower in writing.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Due Date</strong><span style=\"font-weight: 400;\">&rdquo; means the date(s) on which any amounts from the Borrower to the Lender including the principal amounts of the Facility, interest and/or any other Outstanding Amounts, fall due as per </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) of this Agreement or any other Facility Document, or as demanded by the Lender in accordance with a Facility Document.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Events of Default</strong><span style=\"font-weight: 400;\">&rdquo; shall have the meaning ascribed to it under the terms herein.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Facility</strong><span style=\"font-weight: 400;\">&rdquo; means the facility amount mentioned in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">).</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Final Settlement Date</strong><span style=\"font-weight: 400;\">&rdquo; means the date on which all the Outstanding Amounts have been fully paid and the Facility has been irrevocably discharged to the satisfaction of the Lender.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Financing Documents</strong><span style=\"font-weight: 400;\">&rdquo; means this Agreement and such other documents as may be executed or required to be executed between the Lender and/or the Borrower in order to perfect or validate this Agreement.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Government Authority</strong><span style=\"font-weight: 400;\">&rdquo; means any governmental department, commission, board, bureau, agency, regulatory authority, instrumentality, court or other judicial, quasi-judicial or administrative body, whether central, state, provincial or local, having jurisdiction over the subject matter or matters in question.</span> <span style=\"font-weight: 400;\">For avoidance of doubt, it is hereby clarified that the term &ldquo;Government Authority&rdquo; does not include any bank/financial institution acting solely in its capacity as a lender to the Borrower.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Interest Rate</strong><span style=\"font-weight: 400;\">&rdquo; means the rate of interest mentioned in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">).</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Laws</strong><span style=\"font-weight: 400;\">&rdquo; means any statute, law, regulation, ordinance, rule, judgment, order, decree, bye-laws, rule of law, directives, guidelines policy, requirement, or any governmental restriction or any similar form of decision of, or determination by, or any interpretation or administration having the force of law of any of the foregoing, by any Government Authority having jurisdiction over the matter in subject, whether in effect as of the date of this Agreement or hereafter.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Loan Application</strong><span style=\"font-weight: 400;\">&rdquo; means the application made by the Borrower in the form specified by the Lender for availing the Facility and where the context so requires, all other information, particulars submitted by the Borrower to the Lender with a view to avail the Facility.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Material Adverse Effect</strong><span style=\"font-weight: 400;\">&rdquo; means adverse effect on: (a) the ability of the Borrower to observe and perform in a timely manner their respective obligations under any of the Financing Documents to which it is or would be a party or; (b) the legality, validity, binding nature or enforceability of any of the Financing Documents; or (d) the Business or financial condition of the Borrower which is reasonably likely to impair its ability to service the Facility as and when becoming due; or (e) the rights and remedies of the Lender under the Financing Documents.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Outstanding Amounts</strong><span style=\"font-weight: 400;\">&rdquo; mean principal amount of the Facility outstanding from time to time, and all interests, Penal Interest, prepayment charges, costs, commissions, fees &amp; charges, expenses and other amounts due under or in respect of this Agreement.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Payment Mechanism</strong><span style=\"font-weight: 400;\">&rdquo; means UPI, ECS, ACH, NEFT, RTGS, Cash or payment by way of cheque, as the case may be.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Person</strong><span style=\"font-weight: 400;\">&rdquo; shall, unless specifically provided otherwise, mean any individual, corporation, partnership, association of persons, company, joint stock company, trust or Government Authority, as the context may admit.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Prepayment</strong><span style=\"font-weight: 400;\">&rdquo; means the premature repayment of the Facility as per the terms and conditions approved by the Lender in this regard and prevailing at the time of such premature repayment by the Borrower.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Purpose</strong><span style=\"font-weight: 400;\">&rdquo; means the purpose for which the Facility has been agreed to be utilised by the Borrower, as mentioned in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) to this Agreement.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>RBI</strong><span style=\"font-weight: 400;\">&rdquo; means the Reserve Bank of India.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Tax</strong><span style=\"font-weight: 400;\">&rdquo; means any tax, levy, impost, duty or other charge or withholding of a similar nature (including any penalty or interest payable in connection with the failure to pay or delay in paying any of the same).</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Term</strong><span style=\"font-weight: 400;\">&rdquo; or &ldquo;</span><strong>Tenure</strong><span style=\"font-weight: 400;\">&rdquo; means the period as specified in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) of this Agreement, within which the Facility has to be repaid by the Borrower to the Lender along with interest, cost, expenses, fees &amp; charges and other amount as specified in this Agreement.</span></p>\n" +
                    "<p><strong>1.2 Principles of Interpretation</strong><span style=\"font-weight: 400;\">: In this Agreement, unless the context otherwise requires:</span></p>\n" +
                    "<p><strong>T</strong><span style=\"font-weight: 400;\">he headings are for convenience or reference only and shall not be used in and shall not affect the construction or interpretation of this Agreement.</span></p>\n" +
                    "<p><strong>T</strong><span style=\"font-weight: 400;\">he words &ldquo;include&rdquo; and &ldquo;including&rdquo; are to be construed without limitation.</span></p>\n" +
                    "<p><strong>W</strong><span style=\"font-weight: 400;\">ords importing a particular gender shall include all genders.</span></p>\n" +
                    "<p><strong>R</strong><span style=\"font-weight: 400;\">eferences to any law shall include references to such law as it may, after the date of this Agreement, from time to time be amended, supplemented or re-enacted.</span></p>\n" +
                    "<p><strong>T</strong><span style=\"font-weight: 400;\">he Schedule(s) annexed to this Agreement form an integral part of this Agreement and will be of full force and effect as though they were expressly set out in the body of the Agreement;&nbsp;</span></p>\n" +
                    "<p><strong>R</strong><span style=\"font-weight: 400;\">eference to any agreement, including this Agreement, deed, document, instrument, rule, regulation, notification, statute or the like shall mean a reference to the same as may have been duly amended, modified or replaced. For the avoidance of doubt, a document shall be construed as amended, modified or replaced only if such amendment, modification or replacement is executed in compliance with the provisions of such document(s);</span></p>\n" +
                    "<p><strong>I</strong><span style=\"font-weight: 400;\">n the event of any disagreement or dispute between the Lender and the Borrower regarding the materiality or reasonableness of any matter, the opinion of Lender as to the materiality shall be final and binding on the Borrower.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>2.FACILITY</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">2.1 The Lender at the request of the Borrower agrees to grant to the Borrower and the Borrower agrees to borrow from the Lender, the Facility, on the basis and subject to the covenants and terms and conditions set forth herein.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">2.2 If in future, the Borrower approaches the Lender for grant of an additional facility or increase in the amount of Facility, the Lender shall have the sole discretion for granting the same and the Lender can either proceed with&nbsp; the execution of fresh loan agreement with the Borrower or execute a supplemental loan agreement.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">2.3 Disbursement shall be made directly and only to Borrower.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">2.4 The Lender shall have the right to adjust and/or set off any Outstanding Amounts or other dues against any subsequent amount of the Facility due to be disbursed by the Lender to the Borrower.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">2.5 Notwithstanding anything stated herein, the continuation of the Facility shall be at sole and absolute discretion of the Lender and the Lender may at any time in its sole discretion and without assigning any reason call upon the Borrower to pay the Outstanding Balance and upon such demand by the Lender, the Borrower shall, within 48 hours of being so called upon, pay the whole of the Outstanding Balance to the Lender without any delay or demur.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">2.6 The Lender may, at its discretion, maintain appropriate entries in its books of accounts in relation to the Facility and such entries shall be final and binding upon the Borrower.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>3.MODE OF DISBURSAL</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Facility shall be made by the Lender by RTGS/NEFT to the Borrower Account and charges for the same, if any, shall be borne by the Borrower. Such charges shall be deemed to form part of the Outstanding Amounts.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>4.INTEREST</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">4.1 The Borrower shall pay interest on the principal amount of the Facility from time to time at the Interest Rate mentioned in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) to this Agreement.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">4.2 Interest on the Facility will begin to accrue in favour of the Lender as and from the date of disbursal of amount of Facility. Interest shall accrue from day to day and shall be computed on the basis of 365 days a year (irrespective of leap year) and the actual number of days elapsed. However, in the event of the Borrower intends to Prepay the Facility, Interest would be calculated up to the date of actual prepayment, subject to payment of Prepayment charges as applicable.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">4.3 Without prejudice to the Lender's rights, Interest and any other Outstanding Amounts shall be charged/debited to the Borrower Account.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">4.4 Lender at its sole discretion, may change in the prevailing rate of interest on the Facility, either due to change in its policies, or issuance of RBI guidelines and notifications with respect to the same or for any other reason whatsoever and in such an event the term 'Interest Rate' shall for all purposes mean the revised interest rate, which shall always be construed as agreed to be paid by the Borrower and hereby secured.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>5.FEES &amp; REPAYMENT</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.1 The Borrower shall, on or before or after the disbursement of the Facility, bear, pay and reimburse to the Lender all cost, fee, charges, including stamp duty charges, applicable on the Financing Documents and any increased costs expenses incurred and/or to be incurred by the Lender, on a full indemnity basis, in connection with the Facility.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.2 The Borrower shall, on or before the disbursement of the Facility, pay to the Lender/BharatPe processing/service fee calculated at the rate provided in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) to this Agreement, on the amount of the Facility sanctioned by the Lender along-with applicable GST. The processing/service fee shall be non-refundable.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.3 All fees and charges payable by the Borrower to the Lender under this Clause shall be reimbursed by the Borrower to the Lender within 7 (seven) days from the date of notice of demand from the Lender and shall be debited to the Borrower Account.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.4 The Lender have appointed Resilient Innovations Private Limited (BharatPe) having registered office at 90/20, Malviya Nagar, New Delhi 110017 as its collection agent and for such other services as agreed between the Lender and BharatPe, from time to time. All Outstanding Balance shall be payable/paid as may be directed &amp; advised by Lender/BharatPe.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.5 The Borrower shall repay the Facility, if not demanded earlier by Lender pursuant to a Financing Document, as stipulated in and in accordance with and subject to the terms and conditions of the Repayment Schedule set out in </span><strong>Schedule II </strong><span style=\"font-weight: 400;\">(</span><em><span style=\"font-weight: 400;\">Repayment Schedule</span></em><span style=\"font-weight: 400;\">).&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.6 No notice, reminder or intimation in any manner shall be given by the Lender to the Borrower regarding its obligation and responsibility to ensure prompt and regular payment of the Outstanding Amounts to the Lender on Due Dates. It shall be entirely the Borrower's responsibility to ensure prompt and regular payment of the Outstanding Amount.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.7 The Borrower agrees that the repayment of the amount of Facility together with interest, Penal Interest, if any, and all such other sums due and payable by the Borrower to the Lender shall be payable to the Lender Account by way of a Payment Mechanism approved by the Lender, provided that the Lender may, at its sole discretion, require the Borrower to adopt or switch to any alternative mode of payment and the Borrower shall comply with such request, without demur or delay. The Borrower undertakes to remit all Outstanding Amounts to the Lender on the respective Due Date.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.8 Any instruction under the Payment Mechanism which is revoked/ dishonoured shall make the Borrower liable for payment of charges as per the prevailing rules of the Lender in force from time to time, in addition to any Penal Interest that may be levied by the Lender and without prejudice to the Lender's right to take appropriate legal action against the Borrower for such revocation / dishonour.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.9 The Lender expressly reserves its right to call upon the Borrower to pay the whole or part of the Outstanding Amounts at any time after the date of disbursal in the event of a default by the Borrower under any Financing Document.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.10 In the event of any change in Repayment Schedule (at the request of the Borrower or due to an Event of Default), the Borrower shall be liable to pay rescheduling charges at the rate specified in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) to this Agreement. Such payment of rescheduling charges shall be in addition to any other rights and remedies available with the Lender in the Event of Default or otherwise.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>6. SECURITY</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower hereby agrees, undertakes and confirms that it shall deliver to the Lender such security, if applicable, as may be required pursuant to </span><strong>Schedule I </strong><span style=\"font-weight: 400;\">(</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) to this Agreement, as security towards the payment of the Outstanding Amounts with the Lender named as the payee therein.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>7. PENAL INTEREST</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">7.1 Upon occurrence of any of the events mentioned in Article 13 below, the Borrower shall be liable to pay Penal Interest which shall be in addition to the Interest payable by the Borrower under Article 5.1.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">7.2 The Borrower expressly agrees that the rate of Penal Interest is a fair estimate of the loss likely to be suffered by the Lender by reason of such delay/default on the part of the Borrower.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">7.3 Penal Interest shall accrue from day to day and shall be computed on the basis of 365 (three hundred and sixty) days a year (irrespective of leap year).</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">7.4 Penal Interest shall be computed for (i) in case the Penal Interest is payable due to default/delay in any payment, then the period commencing from the Due Date of payment of the amount in default/delay up to the payment of amount in default/delay along-with Penal Interest and (ii) in case of occurrence of any other Event of Default, for the period during which the Event of Default or breach, as the case may be, persists.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>8. PREPAYMENT / FORECLOSURE</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower shall be entitled to prepay/ foreclose the Outstanding Amounts, subject to payment of prepayment charges as set out in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">).</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>9. TAXES</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower shall make all payments to be made by it hereunder without and free from any Tax deduction and/or other deduction and/or withholding and/or statutory levies/duties/charges (&ldquo;</span><strong>Withholding</strong><span style=\"font-weight: 400;\">&rdquo;), unless a Withholding is required by Law.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>10. PURPOSE OF THE FACILITY</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">10.1 The Borrower undertakes and confirms that the entire Facility amount shall be utilized/ deployed only for the Purpose and for no other purpose that shall include without limitation to invest in share market, real estate or in any subsidiary/ associates of the Borrower.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">10.2 Any default, fraud, legal incompetence during the currency of the limits, non-compliance of agreed terms and conditions, non-submission of required papers, any other irregularities by the Borrower will enable the Lender to recall the Facility.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">10.3 The Borrower further confirms and/or undertakes that the Facility shall not be utilized for the following:</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp; 10.3.1 Subscription to or purchase of shares/debentures;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp; 10.3.2 Extending unsecured loans to subsidiary company/ associates or for making inter corporate deposits;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp; 10.3.3 Any speculative purposes or any anti-social purpose or any unlawful purpose.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>11. COVENANTS</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.1 The Borrower agrees to promptly notify, in writing, the Lender about any litigation, arbitration, investigative, regulatory or administrative proceeding/action having a Material Adverse Effect.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.2 All terms and conditions of this Agreement including the Repayment Schedule in relation to the Facility shall remain same even if any amount under the Facility is being taken over by/assigned to any new lender.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.3 The Borrower declares that all the amounts including the amount of own contribution paid/ payable in connection with the Facility, is/ shall be through legitimate source and does not/ shall not constitute an offence of money laundering under the Prevention of Money Laundering Act, 2002.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.4 The Borrower shall perform, on request of the Lender, such acts as may be necessary to carry out the intent of the Financing Documents.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.5 The Borrower shall deliver to the Lender in form and detail, such details, information, documents etc to the satisfaction of the Lender, as may reasonably be required, within such period as required by the Lender from time to time.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.6 In case the Borrower is a body corporate, it shall not induct any person on the board of directors or as partners who have been identified as a wilful defaulter by the RBI. The Borrower confirms that neither it nor any member of its organisation has been declared as wilful defaulter.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.7 The Borrower hereby agrees, undertakes and covenants that unless the Lender otherwise agrees in writing, so long as the Facility or any part thereof is outstanding and an Event of Default has occurred and continuing, until full and final payment of all money owing hereunder, the Borrower </span><strong>SHALL NOT</strong><span style=\"font-weight: 400;\">:</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.7.1 Grant any loans; grant any credit (except in the ordinary course of business) to or for the benefit of any Person other than itself.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.7.2 Allow its principal shareholders/ directors/ promoters/ partners to withdraw monies brought in by them or withdraw the profits earned in the business/capital invested in the business.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>12. REPRESENTATIONS AND WARRANTIES</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">12.1 The Borrower hereby represents and warrants to the Lender on a continuing basis that:</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\">12.1.1&nbsp; Confirmation of Loan Application</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower acknowledges and confirms that all the factual information provided by the Borrower to the Lender in the Loan Application or otherwise in order to avail the Facility and any prior or subsequent information or explanation given to the Lender in this regard is true and accurate in all material respects as at the date it was provided and does not omit to state a material fact necessary in order to make the statements contained therein misleading in the light of the circumstances under which such statements were or are made.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\">12.1.2 Compliance with Laws</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower has complied with all the applicable Laws and is not a party to any litigation, arbitration or administrative or regulatory proceedings or investigations of a material character and that the Borrower is not aware, to the best of its knowledge and belief, of any facts likely to give rise to such litigation, arbitration or administrative or regulatory proceedings or investigations or to material claims against the Borrower.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\">12.1.3 Litigation</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Where applicable, the Borrower shall supply to the Lender, promptly upon becoming aware of them, details of any filing by any creditor (financial creditor or operational creditor) which are made or threatened against them, in accordance with the provisions of the Insolvency and Bankruptcy Code, 2016 or any analogous laws.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\">12.1.4 Compliance of Know Your Customer (KYC) Policy:</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower is fully aware of the KYC Policy of the Lender and RBI and confirms that the information/clarification/documents/signage provided by it on its identity, address, authorised signatory, board resolution, PAN and all other material facts are true and correct and the transaction, etc. are </span><em><span style=\"font-weight: 400;\">bonafide </span></em><span style=\"font-weight: 400;\">and as per Law. The Borrower further confirms that it has disclosed all facts/information as are required to be disclosed for the adherence and compliance of the provisions related to the KYC Policy. The Lender reserve the right to recall the Facility or close the account in case the required documents are not provided by the Borrower to the Lender.</span></p>\n" +
                    "<p>12.1.5 The Lender/BharatPe shall, without notice to or without any consent of the Borrower, be absolutely entitled and have full right, power and authority to make disclosure of any information relating to Borrower including personal information, details in relation to documents, Loan, defaults, security, obligations of Borrower, to the Credit Information Bureau of India (CIBIL) and/or any other governmental/regulatory/statutory or private agency/entity, credit bureau, RBI, the Lender&rsquo;s other branches/ subsidiaries / affiliates / rating agencies, service providers, other Lenders / financial institutions, any third parties, any assignees/potential assignees or transferees, who may need the information and may process the information, publish in such manner and through such medium as may be deemed necessary by the publisher/ Lender/ RBI, including publishing the name as part of willful defaulter&rsquo;s list from time to time, as also use for KYC information verification, credit risk analysis, or for other related purposes. The Borrower waives the privilege of privacy and privity of contract.</p>\n" +
                    "<p>12.1.6 The execution and delivery of this Agreement and documents to be executed in pursuance hereof, and the performance of the Borrower's obligations hereunder and thereunder does not and will not (i) contravene any applicable Law, statute or regulation or any judgment or decree to which any of the Borrowers and/or their Assets and/or business and/or their undertaking is subject, or (ii) conflict with or result in any breach of, any of the terms of or constitute default of any covenants, conditions and stipulations under any existing agreement or contract or binding to which any of the Borrowers are a party or subject or (iii) conflict or contravene any provision of the memorandum and the articles of association and/or any constituting/governing documents of Borrowers.</p>\n" +
                    "<p>12.1.7 The Borrower has informed the Lender about all loans/finances/advances availed by the Borrower from other banks/financial institutions/third parties up to the date of this Agreement to the Lender.</p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">12.1.8 No</span> <span style=\"font-weight: 400;\">default</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower and/or its group companies, affiliates have no over dues/not defaulted in repayment of any amount due and payable to any other bank/financial institutions.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\">12.1.9 Material Adverse Effect</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">There are no facts or circumstances, conditions or occurrences, which could collectively or otherwise be expected to result in the Borrower being unable to perform their respective obligations under the Financing Documents to which they are expressed to be a party, or which could affect the legality, validity, binding nature or enforceability of this Agreement or other Financing Documents or is otherwise expected to have an Material Adverse Effect.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>13. EVENT OF DEFAULT AND CONSEQUENCES</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower expressly and irrevocably hereby agrees and declares that each of the following events or events similar thereto shall constitute an &ldquo;</span><strong>Events of Default</strong><span style=\"font-weight: 400;\">&rdquo;: The following events shall constitute events of default (each an &ldquo;Event of Default&rdquo;), and upon the occurrence of any of them the entire Outstanding Balance shall become immediately due and payable by the Borrower and further enable the Lender inter alia to recall the entire Outstanding Balance and/or enforce any security and transfer/sell the same and/or take, initiate and pursue any actions/proceedings as deemed necessary by the Lender for recovery of the dues, or such other action as the Lender may deem fit: (a) Failure on Borrower&rsquo;s part to perform any of the obligations or terms or conditions or covenants applicable in relation to the Loan including under this document/other documents including non-payment in full of any part of the Outstanding Balance when due or when demanded by Lender/BharatPe; (b) any misrepresentations or misstatement by the Borrower; or (c) occurrence of any circumstance or event which adversely affects Borrower&rsquo;s ability/capacity to pay/repay the Outstanding Balance or any part thereof or perform any of the obligations; (d) the event of death, insolvency, cessation, failure in business of the Borrower, or change or termination of employment/profession/business for any reason whatsoever</span><span style=\"font-weight: 400;\">.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">On and any time after the occurrence of Event of Default, the Lender may, without prejudice to any other rights that it may have under this Agreement or applicable Law (including right to accelerate payment obligations of the Borrower under the Financing Documents) take one or more of the following actions: (a) recall or declare the Outstanding Amounts to be forthwith due and payable, whereupon such amounts shall become forthwith due and payable without presentment, demand, protest or any other notice of any kind, all of which are hereby expressly waived, anything contained herein to the contrary notwithstanding;</span><strong> (b) </strong><span style=\"font-weight: 400;\">exercise any and all rights specified in the Financing Documents including, without limitation, to enforce any security created/provided;</span><strong> (c) </strong><span style=\"font-weight: 400;\">to initiate, appropriate proceedings for recovery of its dues by invoking the jurisdiction of appropriate court at its sole discretion, in addition to taking further action or actions under any other statute in force; and/or (d) exercise such other remedies as permitted or available under applicable law in the sole discretion of the Lender; and/or</span><strong> (e) </strong><span style=\"font-weight: 400;\">disclose the name of the Borrower, and its promoters/directors/partners to RBI, TransUnion CIBIL and/or any other authorised agency.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>14.SUCCESSION</strong></p>\n" +
                    "<p>14.1 In case of the death of the Borrower, where the Borrower is an individual and the Lender agrees to continue extending the Facility, the legal representative of the Borrower, with such other requirements as the Lender may deem fit.&nbsp;</p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>15.MISCELLANEOUS</strong></p>\n" +
                    "<p><span style=\"text-decoration: underline;\">15.1 Governing Law and Jurisdiction</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.1.1 This Agreement and the rights and obligations of the Parties hereunder shall be construed in accordance with and be governed by the laws of India.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.1.2 The Parties agree that the courts in New Delhi shall have exclusive jurisdiction to settle any disputes which may arise out of or in connection with the Financing Documents.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.1.3 The Borrower irrevocably waive any objection, now or in future, to the venue of any Proceedings being the courts at New Delhi or any claim that any such Proceedings have been brought in an inconvenient forum.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.1.4 Nothing contained herein shall limit any right of the Lender to take Proceedings in any other court of competent jurisdiction, nor shall the taking of proceedings in one or more jurisdictions preclude the taking of proceedings in any other jurisdiction whether concurrently or not and the Borrower irrevocably waive any objection it may have now or in the future to the laying of the venue of any Proceedings on the grounds that such Proceedings have been brought in an inconvenient forum.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.2 Arbitration</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.2.1 Without prejudice to the other legal remedies available to the Lender under applicable law (including under the SARFAESI Act, 2002 and Insolvency and Bankruptcy Code, 2016), any dispute arising out of or in connection with the Financing Documents shall be referred to and finally resolved by arbitration under the Arbitration and Conciliation Act, 1996 (as amended from time to time).</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.2.2 The arbitration shall be referred to a sole arbitrator appointed by the Lender. The seat and venue of the arbitration shall be New Delhi. The language of the arbitration and the award of the arbitrator shall be in the English language. The award of the arbitrator shall be final and binding on the Parties and the expenses of the arbitration shall be borne in such manner as the arbitrator may determine.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.3 Indemnity</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Without prejudice to and in addition to other provisions contained in the Financing Documents, the Borrower hereby agrees to indemnify the Lender/BharatPe and its directors, officers, representatives and agents against any losses, liabilities, claims, damages or the like (including, without limitation, reasonable attorneys' fees and expenses) which may be sustained or incurred by any of them as a result of, or in connection with, or arising out of:</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.3.1 the Borrower failing to comply with the provisions of any Financing Documents and applicable Laws; and / or&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.3.2 the occurrence of any Event of Default; and / or</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.3.3 levy by any Government Authority of any charge, Taxes or penalty in connection with regularising or perfecting any of the Financing Documents as may be required under applicable Law at any time during the currency of the Facility, or getting any of the Financing Documents admitted into evidence, or relying on any Financing Documents for proving any claim; and/or</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.3.4 the exercise of any of the rights by the Lender under this Agreement and any of the Financing Documents; and/or</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.3.5 any of the representations and warranties of the Borrower under the Financing Documents are found to be false or untrue or incorrect on a future date</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.4 Confidentiality</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Any information supplied by a Party to another Party pursuant hereto which is by its nature can reasonably be construed to be proprietary or confidential or is marked &ldquo;confidential&rdquo; (&ldquo;</span><strong>Confidential Information</strong><span style=\"font-weight: 400;\">&rdquo;) shall be kept confidential by the recipient unless or until compelled to disclose the same (i) by judicial or administrative process, or (ii) by law, or unless the same (iii) is in or is a part of public domain, or (iv) is required to be furnished to the bankers or investors or potential investors in the either Party, or (v) is required to be furnished to any Government Authority having jurisdiction over the recipient, or (vi) can be shown by the receiving Party to the reasonable satisfaction of the disclosing Party to have been known to the receiving Party prior to it being disclosed by the disclosing Party to the receiving Party, or (vii) subsequently comes lawfully into the possession of the receiving Party from a third party, and in such cases the confidentiality obligations shall cease to the extent required under the foregoing circumstances.&nbsp;</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.5 Amendments and Waivers</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">This Agreement (including the schedules, annexure and appendices hereto) may not be amended, supplemented or modified and no other Financing Document may be amended, supplemented or modified and no term or condition thereof may be waived without the written consent of the Parties to such Financing Document.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.6 Severability</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Any provision of this Agreement or any other Financing Document which is prohibited or unenforceable shall be ineffective to the extent of prohibition or unenforceability but shall not invalidate the remaining provisions of this Agreement or any Financing Document.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.7 Survival</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.7.1 This Agreement shall be in force until all the Outstanding Amounts under this Agreement have been fully and irrevocably paid in accordance with the terms and provisions hereof.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.7.2 The obligations of the Borrower under the Financing Documents will not be affected by:</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">a. any unenforceability, illegality or invalidity of any obligation of any Person under a Financing Document; or</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">b.the breach, frustration or non-fulfilment of any provisions of, or claim arising out of or in connection with a Financing Document.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.8 Right of Set-off</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">In addition to any rights now or hereafter granted under Applicable Law or otherwise, and not by way of limitation of any such rights, upon the occurrence and continuation of an Event of Default, the Lender is hereby authorised by the Borrower to, from time to time, without presentment, demand, protest or other notice of any kind to the Borrower, or to any other Person, set off and/or appropriate and/or apply any and all deposits (general or special) at any time held or owing by the Lender (including, without limitation, by any branches and agencies other than the lending office of Lender) to or for the credit or the account of the Borrower against and on account of the obligations and liabilities of the Borrower to the Lender under this Agreement or under any of the other Financing Documents.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.9 Notices</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">All notices and other communications provided at various places in this Agreement shall be in writing and (a) sent by hand delivery, or (b) prepaid registered post with acknowledgment due, or (c) by e-mail followed by prepaid registered post with acknowledgment due, at the address and/or email first above written. All such notices and communications shall be deemed to have been delivered effective: (i) if sent by email, when sent (provided the email enters the sent folder of the sender), (ii) if sent by prepaid registered post, 3 (three) Business Days after its dispatch.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.10 Effectiveness</span><strong>&nbsp;</strong></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">This Agreement shall become binding on the Parties hereto on and from the date hereof and shall be in force and effect till the Final Settlement Date.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.11 Entire Agreement</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">This Agreement and other Financing Documents shall represent the entire understanding of the Parties on the subject matter hereof and shall override all the previous understanding and agreement between the Parties hereto.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.12 No Discrimination</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower shall, at all times during the term of this Agreement, ensure that no fraudulent preference is given to other lender of the Borrower, both present and future, so as to defeat Lender&rsquo;s rights, either present and future under this Agreement or to fraudulently service the dues owed to other lenders in preference to the dues owed to the Lender or to wilfully act in or consent to any third party acting in a manner as would cause a Material Adverse Effect.</span><span style=\"font-weight: 400;\"><br /></span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.13 Governing Law and Arbitration</span></span></p>\n" +
                    "<div><strong>(a)</strong> This Agreement shall be governed by and construed in accordance with the laws of</div>\n" +
                    "<div>India.</div>\n" +
                    "<div>&nbsp;</div>\n" +
                    "<div><strong>(b)</strong> If any dispute which have arisen or which may arise between the parties with respect</div>\n" +
                    "<div>to the Agreement including its validity, interpretation, implementation or alleged</div>\n" +
                    "<div>breach of any provision of this Agreement (&ldquo;Dispute&rdquo;), the disputing parties hereto</div>\n" +
                    "<div>shall endeavour to settle such Dispute amicably. The attempt to bring about an</div>\n" +
                    "<div>amicable settlement shall be considered to have failed if not resolved within 15</div>\n" +
                    "<div>(fifteen) days from the date of the Dispute.</div>\n" +
                    "<div>&nbsp;</div>\n" +
                    "<div><strong>(c)</strong> Any Dispute which is not resolved pursuant to clause (b) within a</div>\n" +
                    "<div>period of 15 (fifteen) days from the day on which the Dispute arose and which a</div>\n" +
                    "<div>party wishes to have resolved, shall be referred to arbitration. The Lender shall</div>\n" +
                    "<div>appoint a sole arbitrator for the final resolution of such Dispute. The seat of the</div>\n" +
                    "<div>arbitration shall be New Delhi, India. The language of this arbitration shall be</div>\n" +
                    "<div>English.</div>\n" +
                    "<div>&nbsp;</div>\n" +
                    "<div><strong>(d)&nbsp;</strong>The arbitrator shall have the power to grant any legal or equitable remedy or relief</div>\n" +
                    "<div>available under applicable law, including injunctive relief (whether interim and/or</div>\n" +
                    "<div>final) and specific performance and any measures ordered by the arbitrator may be</div>\n" +
                    "<div>specifically enforced by any court of competent jurisdiction.</div>\n" +
                    "<p style=\"text-align: center;\"><strong>SCHEDULE I</strong></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p style=\"text-align: center;\"><strong>TERMS OF THE FACILITY</strong></p>\n" +
                    "<table  border=\"1\" cellspacing=\"0\">\n" +
                    "<tbody>\n" +
                    "<tr >\n" +
                    "<td style=\"width: 40px;\">\n" +
                    "<p><strong>S. NO.</strong></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px;\">\n" +
                    "<p style=\"text-align: center;\"><strong>PARTICULARS</strong></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px;\" colspan=\"2\">\n" +
                    "<p style=\"text-align: center;\"><strong>DETAILS</strong></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 40px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;1.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Date of Agreement</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;" + data.get("createdAt") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr >\n" +
                    "<td style=\"width: 40px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;2.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Place of Agreement</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;height: 36px;\">&nbsp;" + data.get("city") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 84px;\">\n" +
                    "<td style=\"width: 40px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;3.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Loan Agreement No.</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;" + data.get("externalLoanId") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 40px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;4.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Name of Borrower</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;" + data.get("merchantName") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 84px;\">\n" +
                    "<td style=\"width: 40px; height: 84px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;5.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 84px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Address of Borrower</span></p>\n" +
                    "<br />\n" +
                    "<p><span style=\"font-weight: 400;\">Email Address of Borrower</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 84px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;" + data.get("shopNumber") + data.get("streetAddress") + data.get("area") + data.get("landmark") + "</span></p>\n" +
                    "<br />\n" +
                    "<p><span span style=\"font-weight: 400;\">"+data.get("email")+"</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 36px;\">\n" +
                    "<td style=\"width: 40px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;6.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Borrower's constitution</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 36px;\" colspan=\"2\"><br /><br />\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;Individual</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 49px;\">\n" +
                    "<td style=\"width: 40px; height: 49px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;7.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 49px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Purpose of the Facility/ Proposed utilization of the Facility</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 49px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;For&nbsp; General Use</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 36px;\">\n" +
                    "<td style=\"width: 40px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;8.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Amount of Loan</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 36px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;" + data.get("loanAmount") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 62px;\">\n" +
                    "<td style=\"width: 40px; height: 62px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;9.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 62px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Availability Period</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 62px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;</span>" + data.get("payableDays") + " days /" + data.get("tenure") + "</p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 36px;\">\n" +
                    "<td style=\"width: 40px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;10.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Business of the Borrower</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 36px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;" + data.get("businessCategory") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 36px;\">\n" +
                    "<td style=\"width: 40px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;11.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Penal Interest Rate</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 36px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;NIL</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 62px;\">\n" +
                    "<td style=\"width: 40px; height: 62px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;12.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 62px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Interest Rate</span></p>\n" +
                    "<br />\n" +
                    "<p><span style=\"font-weight: 400;\">(a)&nbsp;Interest chargeable&nbsp;</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 62px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;</span></p>\n" +
                    "<br />\n" +
                    "<p><span span style=\"font-weight: 400;\">&nbsp;" + data.get("interestRate") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 61px;\">\n" +
                    "<td style=\"width: 40px; height: 61px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;13.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 61px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Non-refundable Processing Fees /</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">service charge</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 61px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">\n" +
                    "&nbsp;NIL&nbsp;</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "</tbody>\n" +
                    "</table>\n" +
                    "<p><strong style=\"text-align: center;\">TABLE OF CHARGES</strong></p>\n" +
                    "<table style=\"width: 664px;\" border=\"1\" cellspacing=\"0\">\n" +
                    "<tbody>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 117px;\">\n" +
                    "<p><strong>Type of Charges</strong></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 54px;\">&nbsp;</td>\n" +
                    "<td style=\"width: 109px;\">\n" +
                    "<p><strong>Type of Charges</strong></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 42px;\">&nbsp;</td>\n" +
                    "<td style=\"width: 217px;\">\n" +
                    "<p><strong>Type of Charges</strong></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 105px;\">&nbsp;</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 117px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Late payment Charges</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 54px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">NIL</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 109px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Part Prepayment Charges</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 42px;\">NIL</td>\n" +
                    "<td style=\"width: 217px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Title Search Report Charges (Legal Charges)</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 105px;\">NIL</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 117px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Stamping Charges</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 54px;\">NIL</td>\n" +
                    "<td style=\"width: 109px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Processing Fee</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 42px;\">NIL</td>\n" +
                    "<td style=\"width: 217px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Other Charges</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 105px;\">NIL</td>\n" +
                    "</tr>\n" +
                    "</tbody>\n" +
                    "</table>\n" +
                    "<p style=\"text-align: center;\"><strong>SCHEDULE II</strong></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p style=\"text-align: center;\"><strong>REPAYMENT SCHEDULE</strong></p>\n" +
                    "<table style=\"width: 666px;\" border=\"1\" cellspacing=\"0\">\n" +
                    "<tbody>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 40px;\">\n" +
                    "<p><strong>S. No</strong></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 173px;\">\n" +
                    "<p style=\"text-align: center;\"><strong>Particulars</strong></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 431px;\">\n" +
                    "<p style=\"text-align: center;\"><strong>Details</strong></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 40px;\">1.</td>\n" +
                    "<td style=\"width: 173px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Number of EDI</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 431px;\">&nbsp;" + data.get("payableDays") + "</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 40px;\">2.</td>\n" +
                    "<td style=\"width: 173px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Date of Commencement of EDI</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 431px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;" + data.get("createdAt") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 40px;\">3.</td>\n" +
                    "<td style=\"width: 173px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Mode of repayment</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 431px;\">&nbsp;QR Settlement</td>\n" +
                    "</tr>\n" +
                    "</tbody>\n" +
                    "</table>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<table style=\"height: 120px; width: 531px; margin-left: auto; margin-right: auto;\" border=\"1\" cellspacing=\"0\" cellpadding=\"0\">\n" +
                    "<tbody>\n" +
                    "<tr style=\"height: 27px;\">\n" +
                    "<td style=\"width: 189px; height: 27px; text-align: left;\"><strong>Applicant Name:</strong></td>\n" +
                    "<td style=\"width: 326px; text-align: center; height: 27px;\">&nbsp;<strong>" + data.get("merchantName") + "</strong>&nbsp;</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"text-align: center; height: 27px;\">\n" +
                    "<td style=\"width: 189px; height: 27px; text-align: left;\"><strong>Platform:</strong></td>\n" +
                    "<td style=\"width: 326px; height: 27px;\">&nbsp;<strong>Android</strong>&nbsp;</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"text-align: center; height: 26px;\">\n" +
                    "<td style=\"width: 189px; height: 26px; text-align: left;\"><strong>Browser :</strong></td>\n" +
                    "<td style=\"width: 326px; height: 26px;\">&nbsp;<strong>" + data.get("browserName") + "</strong>&nbsp;</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"text-align: center; height: 30px;\">\n" +
                    "<td style=\"width: 189px; height: 30px; text-align: left;\"><strong>IP Address:</strong></td>\n" +
                    "<td style=\"width: 326px; height: 30px;\">&nbsp;<strong>" + data.get("ipAddress") + "</strong>&nbsp;</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"text-align: center; height: 26px;\">\n" +
                    "<td style=\"width: 189px; height: 26px; text-align: left;\"><strong>Mobile Number for eSign:</strong></td>\n" +
                    "<td style=\"width: 326px; height: 26px;\">&nbsp;<strong>" + data.get("mobile") + "</strong>&nbsp;</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"text-align: center; height: 26px;\">\n" +
                    "<td style=\"width: 189px; height: 26px; text-align: left;\"><strong>Timestamp :</strong></td>\n" +
                    "<td style=\"width: 326px; height: 26px;\">&nbsp;<strong>" + data.get("createdAt") + "</strong>&nbsp;</td>\n" +
                    "</tr>\n" +
                    "</tbody>\n" +
                    "</table>\n" +
                    "<p style=\"text-align: left;\"><br /><strong>Date:&nbsp;" + data.get("createdAt") + "   &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;&nbsp; &nbsp; &nbsp;Place:&nbsp;"+data.get("city")+"</strong></p>";
        }else if("HINDON".equalsIgnoreCase(lender)){
            html = "<p style=\"text-align: center;\"><strong>Loan Details</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Loan ID: " + data.get("externalLoanId") + "</span>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span style=\"font-weight: 400;\">Date:" + data.get("agreementAt") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Loan Amount (INR):&nbsp; " + data.get("loanAmount") + "</span> &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span style=\"font-weight: 400;\">Tenure (Months):&nbsp; &nbsp; " + data.get("tenure") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Flat Rate of Interest&nbsp;(% per month) : &nbsp;&nbsp;" + data.get("interestRate") + " %</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Flat Rate of Interest&nbsp; (% per annum) :&nbsp;&nbsp;" + data.get("interestRateAnnum") + " %</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Amount of EDI : &nbsp; " + data.get("edi") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Processing fees by BharatPe, if any &nbsp;&nbsp;" + data.get("processingFee") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">BharatPe Registered Mobile Number: " + data.get("mobile") + "</span> <span style=\"font-weight: 400;\"></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Location: " + data.get("location") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">EDI Due Date - Every day from Monday to Saturday from the successive day of disbursal&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Shop/Business Address: " + data.get("shopNumber") + data.get("streetAddress") + data.get("landmark") + data.get("area") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Landmark: " + data.get("landmark") + " &nbsp;&nbsp;&nbsp; PIN: " + data.get("pincode") + " &nbsp;&nbsp;&nbsp;City: " + data.get("city") + " &nbsp;&nbsp;&nbsp; State: " + data.get("state") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Email: " + data.get("email") + "</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Shop/ Business Phone Number: " + data.get("mobile") + "</span></p>\n" +
                    "<h2 style=\"text-align: left;\"><strong>Declaration / Undertaking/Representation by Borrower (MITC)</strong></h2>\n" +
                    "<p><span style=\"font-weight: 400;\">1. I/We hereby apply for a finance facility as proposition made by </span><strong>Resilient Innovation Private Limited (&ldquo;BharatPe&rdquo;)</strong><span style=\"font-weight: 400;\"> as in terms of Loan Agreement as below and declare that all the particulars, information and details provided and other documents submitted by me/us are true, correct, complete and up-to-date in all respects and that I/We have not withheld any material information.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">2. I/We hereby authorize Lender/BharatPe to exchange or share information and details relating to my application to its group companies or any third party, as may be required or deemed fit, for the purpose of processing this loan application and/or related offerings or other products / services that I/We may apply for from time to time.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">3. By submitting this application, I/We hereby expressly authorize Lender/BharatPe to send me communications regarding loans, insurance and other products from Lender/BharatPe, its group companies and / or third parties through telephone calls / SMSs / emails / post etc. including but not limited to promotional, transactional communications. I/We confirm that I shall not challenge receipt of such communications by me as unsolicited communication, defined under TRAI Regulations on Unsolicited Commercial Communications.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">4. I authorize BharatPe / Lender to evaluate my transaction history on the BharatPe platform in order to check my eligibility for the loan and understand and acknowledge that Lender/BharatPe has the absolute discretion, without assigning any reasons to reject my application and that Lender/BharatPe is not answerable / liable to me, in any manner whatsoever, for rejecting my application.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.I / We agrees and accept that Lender/BharatPe may in its sole discretion, by its self or through authorised persons, advocate, agencies, bureau, etc. verify any information given, check credit references, employment details and obtain credit reports to determine creditworthiness from time to time.</span></p>\n" +
                    "<p><strong>LOAN AGREEMENT</strong></p>\n" +
                    "<br/>\n" +
                    "<p><span style=\"font-weight: 400;\">This </span><strong>Loan Agreement</strong><span style=\"font-weight: 400;\"> (&ldquo;</span><strong>Agreement</strong><span style=\"font-weight: 400;\">&rdquo;) is made and executed at the place mentioned in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) and on the date mentioned in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) by and between:</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">HINDON MERCANTILE LIMITED, a non-banking finance company, having its registered office at Unit No 307, Third Floor Plot No. H-1 Garg Tower, NSP, Pitampura Delhi (hereinafter referred to as the &ldquo;</span><strong>Lender</strong><span style=\"font-weight: 400;\">&rdquo; which expression shall, unless repugnant to the context or meaning thereof, be deemed to mean and include its successor(s) and permitted assign(s)) of the One Part;</span></p>\n" +
                    "<p><strong>AND</strong></p>"+
                    "<p><strong><em>[Details from the Schedule I]</em></strong><span style=\"font-weight: 400;\">,</span> <span style=\"font-weight: 400;\">hereto as the borrower and co-borrower (if any) (wherever the context so requires) (hereinafter referred to as the &ldquo;</span><strong>Borrower</strong><span style=\"font-weight: 400;\">&rdquo; which expression shall, unless repugnant to the context or meaning thereof, be deemed to mean and include his/her/their heir(s), successor(s), legal representative(s), executor(s), administrator(s) and permitted assign(s)) of the Other Part.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Lender and the Borrower are hereinafter collectively referred to as the &ldquo;</span><strong>Parties</strong><span style=\"font-weight: 400;\">&rdquo; and each individually as the &ldquo;</span><strong>Party</strong><span style=\"font-weight: 400;\">&rdquo;.</span></p>\n" +
                    "<p><strong>WHEREAS</strong><span style=\"font-weight: 400;\">:</span></p>\n" +
                    "<ol>\n" +
                    "<li style=\"font-weight: 400;\"><span style=\"font-weight: 400;\">The Lender is a non-banking financing company, registered with the Reserve Bank of India, having registration no. B.05.05070, and is </span><em><span style=\"font-weight: 400;\">inter alia</span></em><span style=\"font-weight: 400;\"> engaged in the business of advancing loans and other financial facilities.</span></li>\n" +
                    "<li style=\"font-weight: 400;\"><span style=\"font-weight: 400;\">The Borrower has approached the Lender and has requested for grant of loan facility for the purpose of </span><strong><em>as mentioned in Schedule I </em></strong><span style=\"font-weight: 400;\">and in reliance on the acceptance of the terms, conditions, assurances, representations and warranties of the Borrower, the Lender has agreed to grant loan facility to the Borrower, subject to the terms and conditions contained in this Agreement.</span></li>\n" +
                    "<li style=\"font-weight: 400;\"><span style=\"font-weight: 400;\">The Parties hereto are now desirous of </span><em><span style=\"font-weight: 400;\">inter alia</span></em><span style=\"font-weight: 400;\"> entering into this Agreement to set out the terms and conditions in relation to the Facility.</span></li>\n" +
                    "</ol>\n" +
                    "<p><strong>Now, therefore, in view of the foregoing and in consideration of the mutual covenants and agreements herein set forth, the parties hereby agree as follows:</strong></p>\n" +
                    "<br/>\n" +
                    "<p><strong>1.DEFINITIONS AND INTERPRETATION</strong></p>\n" +
                    "<p><strong>1.1 Definitions</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Borrower Account</strong><span style=\"font-weight: 400;\">&rdquo; means the following bank account of the Borrower </span><strong><em>as mentioned in Schedule I</em></strong><span style=\"font-weight: 400;\">, unless otherwise notified by the Borrower in writing.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Due Date</strong><span style=\"font-weight: 400;\">&rdquo; means the date(s) on which any amounts from the Borrower to the Lender including the principal amounts of the Facility, interest and/or any other Outstanding Amounts, fall due as per </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) of this Agreement or any other Facility Document, or as demanded by the Lender in accordance with a Facility Document.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Events of Default</strong><span style=\"font-weight: 400;\">&rdquo; shall have the meaning ascribed to it under the terms herein.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Facility</strong><span style=\"font-weight: 400;\">&rdquo; means the facility amount mentioned in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">).</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Final Settlement Date</strong><span style=\"font-weight: 400;\">&rdquo; means the date on which all the Outstanding Amounts have been fully paid and the Facility has been irrevocably discharged to the satisfaction of the Lender.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Financing Documents</strong><span style=\"font-weight: 400;\">&rdquo; means this Agreement and such other documents as may be executed or required to be executed between the Lender and/or the Borrower in order to perfect or validate this Agreement.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Government Authority</strong><span style=\"font-weight: 400;\">&rdquo; means any governmental department, commission, board, bureau, agency, regulatory authority, instrumentality, court or other judicial, quasi-judicial or administrative body, whether central, state, provincial or local, having jurisdiction over the subject matter or matters in question.</span> <span style=\"font-weight: 400;\">For avoidance of doubt, it is hereby clarified that the term &ldquo;Government Authority&rdquo; does not include any bank/financial institution acting solely in its capacity as a lender to the Borrower.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Interest Rate</strong><span style=\"font-weight: 400;\">&rdquo; means the rate of interest mentioned in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">).</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Laws</strong><span style=\"font-weight: 400;\">&rdquo; means any statute, law, regulation, ordinance, rule, judgment, order, decree, bye-laws, rule of law, directives, guidelines policy, requirement, or any governmental restriction or any similar form of decision of, or determination by, or any interpretation or administration having the force of law of any of the foregoing, by any Government Authority having jurisdiction over the matter in subject, whether in effect as of the date of this Agreement or hereafter.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Loan Application</strong><span style=\"font-weight: 400;\">&rdquo; means the application made by the Borrower in the form specified by the Lender for availing the Facility and where the context so requires, all other information, particulars submitted by the Borrower to the Lender with a view to avail the Facility.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Material Adverse Effect</strong><span style=\"font-weight: 400;\">&rdquo; means adverse effect on: (a) the ability of the Borrower to observe and perform in a timely manner their respective obligations under any of the Financing Documents to which it is or would be a party or; (b) the legality, validity, binding nature or enforceability of any of the Financing Documents; or (d) the Business or financial condition of the Borrower which is reasonably likely to impair its ability to service the Facility as and when becoming due; or (e) the rights and remedies of the Lender under the Financing Documents.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Outstanding Amounts</strong><span style=\"font-weight: 400;\">&rdquo; mean principal amount of the Facility outstanding from time to time, and all interests, Penal Interest, prepayment charges, costs, commissions, fees &amp; charges, expenses and other amounts due under or in respect of this Agreement.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Payment Mechanism</strong><span style=\"font-weight: 400;\">&rdquo; means UPI, ECS, ACH, NEFT, RTGS, Cash or payment by way of cheque, as the case may be.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Person</strong><span style=\"font-weight: 400;\">&rdquo; shall, unless specifically provided otherwise, mean any individual, corporation, partnership, association of persons, company, joint stock company, trust or Government Authority, as the context may admit.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Prepayment</strong><span style=\"font-weight: 400;\">&rdquo; means the premature repayment of the Facility as per the terms and conditions approved by the Lender in this regard and prevailing at the time of such premature repayment by the Borrower.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Purpose</strong><span style=\"font-weight: 400;\">&rdquo; means the purpose for which the Facility has been agreed to be utilised by the Borrower, as mentioned in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) to this Agreement.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>RBI</strong><span style=\"font-weight: 400;\">&rdquo; means the Reserve Bank of India.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Tax</strong><span style=\"font-weight: 400;\">&rdquo; means any tax, levy, impost, duty or other charge or withholding of a similar nature (including any penalty or interest payable in connection with the failure to pay or delay in paying any of the same).</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&ldquo;</span><strong>Term</strong><span style=\"font-weight: 400;\">&rdquo; or &ldquo;</span><strong>Tenure</strong><span style=\"font-weight: 400;\">&rdquo; means the period as specified in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) of this Agreement, within which the Facility has to be repaid by the Borrower to the Lender along with interest, cost, expenses, fees &amp; charges and other amount as specified in this Agreement.</span></p>\n" +
                    "<p><strong>1.2 Principles of Interpretation</strong><span style=\"font-weight: 400;\">: In this Agreement, unless the context otherwise requires:</span></p>\n" +
                    "<p><strong>T</strong><span style=\"font-weight: 400;\">he headings are for convenience or reference only and shall not be used in and shall not affect the construction or interpretation of this Agreement.</span></p>\n" +
                    "<p><strong>T</strong><span style=\"font-weight: 400;\">he words &ldquo;include&rdquo; and &ldquo;including&rdquo; are to be construed without limitation.</span></p>\n" +
                    "<p><strong>W</strong><span style=\"font-weight: 400;\">ords importing a particular gender shall include all genders.</span></p>\n" +
                    "<p><strong>R</strong><span style=\"font-weight: 400;\">eferences to any law shall include references to such law as it may, after the date of this Agreement, from time to time be amended, supplemented or re-enacted.</span></p>\n" +
                    "<p><strong>T</strong><span style=\"font-weight: 400;\">he Schedule(s) annexed to this Agreement form an integral part of this Agreement and will be of full force and effect as though they were expressly set out in the body of the Agreement;&nbsp;</span></p>\n" +
                    "<p><strong>R</strong><span style=\"font-weight: 400;\">eference to any agreement, including this Agreement, deed, document, instrument, rule, regulation, notification, statute or the like shall mean a reference to the same as may have been duly amended, modified or replaced. For the avoidance of doubt, a document shall be construed as amended, modified or replaced only if such amendment, modification or replacement is executed in compliance with the provisions of such document(s);</span></p>\n" +
                    "<p><strong>I</strong><span style=\"font-weight: 400;\">n the event of any disagreement or dispute between the Lender and the Borrower regarding the materiality or reasonableness of any matter, the opinion of Lender as to the materiality shall be final and binding on the Borrower.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>2.FACILITY</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">2.1 The Lender at the request of the Borrower agrees to grant to the Borrower and the Borrower agrees to borrow from the Lender, the Facility, on the basis and subject to the covenants and terms and conditions set forth herein.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">2.2 If in future, the Borrower approaches the Lender for grant of an additional facility or increase in the amount of Facility, the Lender shall have the sole discretion for granting the same and the Lender can either proceed with&nbsp; the execution of fresh loan agreement with the Borrower or execute a supplemental loan agreement.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">2.3 Disbursement shall be made directly and only to Borrower.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">2.4 The Lender shall have the right to adjust and/or set off any Outstanding Amounts or other dues against any subsequent amount of the Facility due to be disbursed by the Lender to the Borrower.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">2.5 Notwithstanding anything stated herein, the continuation of the Facility shall be at sole and absolute discretion of the Lender and the Lender may at any time in its sole discretion and without assigning any reason call upon the Borrower to pay the Outstanding Balance and upon such demand by the Lender, the Borrower shall, within 48 hours of being so called upon, pay the whole of the Outstanding Balance to the Lender without any delay or demur.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">2.6 The Lender may, at its discretion, maintain appropriate entries in its books of accounts in relation to the Facility and such entries shall be final and binding upon the Borrower.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>3.MODE OF DISBURSAL</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Facility shall be made by the Lender by RTGS/NEFT to the Borrower Account and charges for the same, if any, shall be borne by the Borrower. Such charges shall be deemed to form part of the Outstanding Amounts.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>4.INTEREST</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">4.1 The Borrower shall pay interest on the principal amount of the Facility from time to time at the Interest Rate mentioned in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) to this Agreement.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">4.2 Interest on the Facility will begin to accrue in favour of the Lender as and from the date of disbursal of amount of Facility. Interest shall accrue from day to day and shall be computed on the basis of 365 days a year (irrespective of leap year) and the actual number of days elapsed. However, in the event of the Borrower intends to Prepay the Facility, Interest would be calculated up to the date of actual prepayment, subject to payment of Prepayment charges as applicable.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">4.3 Without prejudice to the Lender's rights, Interest and any other Outstanding Amounts shall be charged/debited to the Borrower Account.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">4.4 Lender at its sole discretion, may change in the prevailing rate of interest on the Facility, either due to change in its policies, or issuance of RBI guidelines and notifications with respect to the same or for any other reason whatsoever and in such an event the term 'Interest Rate' shall for all purposes mean the revised interest rate, which shall always be construed as agreed to be paid by the Borrower and hereby secured.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>5.FEES &amp; REPAYMENT</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.1 The Borrower shall, on or before or after the disbursement of the Facility, bear, pay and reimburse to the Lender all cost, fee, charges, including stamp duty charges, applicable on the Financing Documents and any increased costs expenses incurred and/or to be incurred by the Lender, on a full indemnity basis, in connection with the Facility.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.2 The Borrower shall, on or before the disbursement of the Facility, pay to the Lender/BharatPe processing/service fee calculated at the rate provided in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) to this Agreement, on the amount of the Facility sanctioned by the Lender along-with applicable GST. The processing/service fee shall be non-refundable.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.3 All fees and charges payable by the Borrower to the Lender under this Clause shall be reimbursed by the Borrower to the Lender within 7 (seven) days from the date of notice of demand from the Lender and shall be debited to the Borrower Account.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.4 The Lender have appointed Resilient Innovations Private Limited (BharatPe) having registered office at 90/20, Malviya Nagar, New Delhi 110017 as its collection agent and for such other services as agreed between the Lender and BharatPe, from time to time. All Outstanding Balance shall be payable/paid as may be directed &amp; advised by Lender/BharatPe.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.5 The Borrower shall repay the Facility, if not demanded earlier by Lender pursuant to a Financing Document, as stipulated in and in accordance with and subject to the terms and conditions of the Repayment Schedule set out in </span><strong>Schedule II </strong><span style=\"font-weight: 400;\">(</span><em><span style=\"font-weight: 400;\">Repayment Schedule</span></em><span style=\"font-weight: 400;\">).&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.6 No notice, reminder or intimation in any manner shall be given by the Lender to the Borrower regarding its obligation and responsibility to ensure prompt and regular payment of the Outstanding Amounts to the Lender on Due Dates. It shall be entirely the Borrower's responsibility to ensure prompt and regular payment of the Outstanding Amount.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.7 The Borrower agrees that the repayment of the amount of Facility together with interest, Penal Interest, if any, and all such other sums due and payable by the Borrower to the Lender shall be payable to the Lender Account by way of a Payment Mechanism approved by the Lender, provided that the Lender may, at its sole discretion, require the Borrower to adopt or switch to any alternative mode of payment and the Borrower shall comply with such request, without demur or delay. The Borrower undertakes to remit all Outstanding Amounts to the Lender on the respective Due Date.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.8 Any instruction under the Payment Mechanism which is revoked/ dishonoured shall make the Borrower liable for payment of charges as per the prevailing rules of the Lender in force from time to time, in addition to any Penal Interest that may be levied by the Lender and without prejudice to the Lender's right to take appropriate legal action against the Borrower for such revocation / dishonour.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.9 The Lender expressly reserves its right to call upon the Borrower to pay the whole or part of the Outstanding Amounts at any time after the date of disbursal in the event of a default by the Borrower under any Financing Document.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">5.10 In the event of any change in Repayment Schedule (at the request of the Borrower or due to an Event of Default), the Borrower shall be liable to pay rescheduling charges at the rate specified in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) to this Agreement. Such payment of rescheduling charges shall be in addition to any other rights and remedies available with the Lender in the Event of Default or otherwise.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>6. SECURITY</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower hereby agrees, undertakes and confirms that it shall deliver to the Lender such security, if applicable, as may be required pursuant to </span><strong>Schedule I </strong><span style=\"font-weight: 400;\">(</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">) to this Agreement, as security towards the payment of the Outstanding Amounts with the Lender named as the payee therein.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>7. PENAL INTEREST</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">7.1 Upon occurrence of any of the events mentioned in Article 13 below, the Borrower shall be liable to pay Penal Interest which shall be in addition to the Interest payable by the Borrower under Article 5.1.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">7.2 The Borrower expressly agrees that the rate of Penal Interest is a fair estimate of the loss likely to be suffered by the Lender by reason of such delay/default on the part of the Borrower.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">7.3 Penal Interest shall accrue from day to day and shall be computed on the basis of 365 (three hundred and sixty) days a year (irrespective of leap year).</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">7.4 Penal Interest shall be computed for (i) in case the Penal Interest is payable due to default/delay in any payment, then the period commencing from the Due Date of payment of the amount in default/delay up to the payment of amount in default/delay along-with Penal Interest and (ii) in case of occurrence of any other Event of Default, for the period during which the Event of Default or breach, as the case may be, persists.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>8. PREPAYMENT / FORECLOSURE</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower shall be entitled to prepay/ foreclose the Outstanding Amounts, subject to payment of prepayment charges as set out in </span><strong>Schedule I</strong><span style=\"font-weight: 400;\"> (</span><em><span style=\"font-weight: 400;\">Terms of the Facility</span></em><span style=\"font-weight: 400;\">).</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>9. TAXES</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower shall make all payments to be made by it hereunder without and free from any Tax deduction and/or other deduction and/or withholding and/or statutory levies/duties/charges (&ldquo;</span><strong>Withholding</strong><span style=\"font-weight: 400;\">&rdquo;), unless a Withholding is required by Law.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>10. PURPOSE OF THE FACILITY</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">10.1 The Borrower undertakes and confirms that the entire Facility amount shall be utilized/ deployed only for the Purpose and for no other purpose that shall include without limitation to invest in share market, real estate or in any subsidiary/ associates of the Borrower.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">10.2 Any default, fraud, legal incompetence during the currency of the limits, non-compliance of agreed terms and conditions, non-submission of required papers, any other irregularities by the Borrower will enable the Lender to recall the Facility.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">10.3 The Borrower further confirms and/or undertakes that the Facility shall not be utilized for the following:</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp; 10.3.1 Subscription to or purchase of shares/debentures;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp; 10.3.2 Extending unsecured loans to subsidiary company/ associates or for making inter corporate deposits;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp; 10.3.3 Any speculative purposes or any anti-social purpose or any unlawful purpose.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>11. COVENANTS</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.1 The Borrower agrees to promptly notify, in writing, the Lender about any litigation, arbitration, investigative, regulatory or administrative proceeding/action having a Material Adverse Effect.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.2 All terms and conditions of this Agreement including the Repayment Schedule in relation to the Facility shall remain same even if any amount under the Facility is being taken over by/assigned to any new lender.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.3 The Borrower declares that all the amounts including the amount of own contribution paid/ payable in connection with the Facility, is/ shall be through legitimate source and does not/ shall not constitute an offence of money laundering under the Prevention of Money Laundering Act, 2002.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.4 The Borrower shall perform, on request of the Lender, such acts as may be necessary to carry out the intent of the Financing Documents.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.5 The Borrower shall deliver to the Lender in form and detail, such details, information, documents etc to the satisfaction of the Lender, as may reasonably be required, within such period as required by the Lender from time to time.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.6 In case the Borrower is a body corporate, it shall not induct any person on the board of directors or as partners who have been identified as a wilful defaulter by the RBI. The Borrower confirms that neither it nor any member of its organisation has been declared as wilful defaulter.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.7 The Borrower hereby agrees, undertakes and covenants that unless the Lender otherwise agrees in writing, so long as the Facility or any part thereof is outstanding and an Event of Default has occurred and continuing, until full and final payment of all money owing hereunder, the Borrower </span><strong>SHALL NOT</strong><span style=\"font-weight: 400;\">:</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.7.1 Grant any loans; grant any credit (except in the ordinary course of business) to or for the benefit of any Person other than itself.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">11.7.2 Allow its principal shareholders/ directors/ promoters/ partners to withdraw monies brought in by them or withdraw the profits earned in the business/capital invested in the business.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>12. REPRESENTATIONS AND WARRANTIES</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">12.1 The Borrower hereby represents and warrants to the Lender on a continuing basis that:</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\">12.1.1&nbsp; Confirmation of Loan Application</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower acknowledges and confirms that all the factual information provided by the Borrower to the Lender in the Loan Application or otherwise in order to avail the Facility and any prior or subsequent information or explanation given to the Lender in this regard is true and accurate in all material respects as at the date it was provided and does not omit to state a material fact necessary in order to make the statements contained therein misleading in the light of the circumstances under which such statements were or are made.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\">12.1.2 Compliance with Laws</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower has complied with all the applicable Laws and is not a party to any litigation, arbitration or administrative or regulatory proceedings or investigations of a material character and that the Borrower is not aware, to the best of its knowledge and belief, of any facts likely to give rise to such litigation, arbitration or administrative or regulatory proceedings or investigations or to material claims against the Borrower.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\">12.1.3 Litigation</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Where applicable, the Borrower shall supply to the Lender, promptly upon becoming aware of them, details of any filing by any creditor (financial creditor or operational creditor) which are made or threatened against them, in accordance with the provisions of the Insolvency and Bankruptcy Code, 2016 or any analogous laws.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\">12.1.4 Compliance of Know Your Customer (KYC) Policy:</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower is fully aware of the KYC Policy of the Lender and RBI and confirms that the information/clarification/documents/signage provided by it on its identity, address, authorised signatory, board resolution, PAN and all other material facts are true and correct and the transaction, etc. are </span><em><span style=\"font-weight: 400;\">bonafide </span></em><span style=\"font-weight: 400;\">and as per Law. The Borrower further confirms that it has disclosed all facts/information as are required to be disclosed for the adherence and compliance of the provisions related to the KYC Policy. The Lender reserve the right to recall the Facility or close the account in case the required documents are not provided by the Borrower to the Lender.</span></p>\n" +
                    "<p>12.1.5 The Lender/BharatPe shall, without notice to or without any consent of the Borrower, be absolutely entitled and have full right, power and authority to make disclosure of any information relating to Borrower including personal information, details in relation to documents, Loan, defaults, security, obligations of Borrower, to the Credit Information Bureau of India (CIBIL) and/or any other governmental/regulatory/statutory or private agency/entity, credit bureau, RBI, the Lender&rsquo;s other branches/ subsidiaries / affiliates / rating agencies, service providers, other Lenders / financial institutions, any third parties, any assignees/potential assignees or transferees, who may need the information and may process the information, publish in such manner and through such medium as may be deemed necessary by the publisher/ Lender/ RBI, including publishing the name as part of willful defaulter&rsquo;s list from time to time, as also use for KYC information verification, credit risk analysis, or for other related purposes. The Borrower waives the privilege of privacy and privity of contract.</p>\n" +
                    "<p>12.1.6 The execution and delivery of this Agreement and documents to be executed in pursuance hereof, and the performance of the Borrower's obligations hereunder and thereunder does not and will not (i) contravene any applicable Law, statute or regulation or any judgment or decree to which any of the Borrowers and/or their Assets and/or business and/or their undertaking is subject, or (ii) conflict with or result in any breach of, any of the terms of or constitute default of any covenants, conditions and stipulations under any existing agreement or contract or binding to which any of the Borrowers are a party or subject or (iii) conflict or contravene any provision of the memorandum and the articles of association and/or any constituting/governing documents of Borrowers.</p>\n" +
                    "<p>12.1.7 The Borrower has informed the Lender about all loans/finances/advances availed by the Borrower from other banks/financial institutions/third parties up to the date of this Agreement to the Lender.</p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">12.1.8 No</span> <span style=\"font-weight: 400;\">default</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower and/or its group companies, affiliates have no over dues/not defaulted in repayment of any amount due and payable to any other bank/financial institutions.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\">12.1.9 Material Adverse Effect</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">There are no facts or circumstances, conditions or occurrences, which could collectively or otherwise be expected to result in the Borrower being unable to perform their respective obligations under the Financing Documents to which they are expressed to be a party, or which could affect the legality, validity, binding nature or enforceability of this Agreement or other Financing Documents or is otherwise expected to have an Material Adverse Effect.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>13. EVENT OF DEFAULT AND CONSEQUENCES</strong></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower expressly and irrevocably hereby agrees and declares that each of the following events or events similar thereto shall constitute an &ldquo;</span><strong>Events of Default</strong><span style=\"font-weight: 400;\">&rdquo;: The following events shall constitute events of default (each an &ldquo;Event of Default&rdquo;), and upon the occurrence of any of them the entire Outstanding Balance shall become immediately due and payable by the Borrower and further enable the Lender inter alia to recall the entire Outstanding Balance and/or enforce any security and transfer/sell the same and/or take, initiate and pursue any actions/proceedings as deemed necessary by the Lender for recovery of the dues, or such other action as the Lender may deem fit: (a) Failure on Borrower&rsquo;s part to perform any of the obligations or terms or conditions or covenants applicable in relation to the Loan including under this document/other documents including non-payment in full of any part of the Outstanding Balance when due or when demanded by Lender/BharatPe; (b) any misrepresentations or misstatement by the Borrower; or (c) occurrence of any circumstance or event which adversely affects Borrower&rsquo;s ability/capacity to pay/repay the Outstanding Balance or any part thereof or perform any of the obligations; (d) the event of death, insolvency, cessation, failure in business of the Borrower, or change or termination of employment/profession/business for any reason whatsoever</span><span style=\"font-weight: 400;\">.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">On and any time after the occurrence of Event of Default, the Lender may, without prejudice to any other rights that it may have under this Agreement or applicable Law (including right to accelerate payment obligations of the Borrower under the Financing Documents) take one or more of the following actions: (a) recall or declare the Outstanding Amounts to be forthwith due and payable, whereupon such amounts shall become forthwith due and payable without presentment, demand, protest or any other notice of any kind, all of which are hereby expressly waived, anything contained herein to the contrary notwithstanding;</span><strong> (b) </strong><span style=\"font-weight: 400;\">exercise any and all rights specified in the Financing Documents including, without limitation, to enforce any security created/provided;</span><strong> (c) </strong><span style=\"font-weight: 400;\">to initiate, appropriate proceedings for recovery of its dues by invoking the jurisdiction of appropriate court at its sole discretion, in addition to taking further action or actions under any other statute in force; and/or (d) exercise such other remedies as permitted or available under applicable law in the sole discretion of the Lender; and/or</span><strong> (e) </strong><span style=\"font-weight: 400;\">disclose the name of the Borrower, and its promoters/directors/partners to RBI, TransUnion CIBIL and/or any other authorised agency.</span></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>14.SUCCESSION</strong></p>\n" +
                    "<p>14.1 In case of the death of the Borrower, where the Borrower is an individual and the Lender agrees to continue extending the Facility, the legal representative of the Borrower, with such other requirements as the Lender may deem fit.&nbsp;</p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p><strong>15.MISCELLANEOUS</strong></p>\n" +
                    "<p><span style=\"text-decoration: underline;\">15.1 Governing Law and Jurisdiction</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.1.1 This Agreement and the rights and obligations of the Parties hereunder shall be construed in accordance with and be governed by the laws of India.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.1.2 The Parties agree that the courts in New Delhi shall have exclusive jurisdiction to settle any disputes which may arise out of or in connection with the Financing Documents.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.1.3 The Borrower irrevocably waive any objection, now or in future, to the venue of any Proceedings being the courts at New Delhi or any claim that any such Proceedings have been brought in an inconvenient forum.&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.1.4 Nothing contained herein shall limit any right of the Lender to take Proceedings in any other court of competent jurisdiction, nor shall the taking of proceedings in one or more jurisdictions preclude the taking of proceedings in any other jurisdiction whether concurrently or not and the Borrower irrevocably waive any objection it may have now or in the future to the laying of the venue of any Proceedings on the grounds that such Proceedings have been brought in an inconvenient forum.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.2 Arbitration</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.2.1 Without prejudice to the other legal remedies available to the Lender under applicable law (including under the SARFAESI Act, 2002 and Insolvency and Bankruptcy Code, 2016), any dispute arising out of or in connection with the Financing Documents shall be referred to and finally resolved by arbitration under the Arbitration and Conciliation Act, 1996 (as amended from time to time).</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.2.2 The arbitration shall be referred to a sole arbitrator appointed by the Lender. The seat and venue of the arbitration shall be New Delhi. The language of the arbitration and the award of the arbitrator shall be in the English language. The award of the arbitrator shall be final and binding on the Parties and the expenses of the arbitration shall be borne in such manner as the arbitrator may determine.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.3 Indemnity</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Without prejudice to and in addition to other provisions contained in the Financing Documents, the Borrower hereby agrees to indemnify the Lender/BharatPe and its directors, officers, representatives and agents against any losses, liabilities, claims, damages or the like (including, without limitation, reasonable attorneys' fees and expenses) which may be sustained or incurred by any of them as a result of, or in connection with, or arising out of:</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.3.1 the Borrower failing to comply with the provisions of any Financing Documents and applicable Laws; and / or&nbsp;</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.3.2 the occurrence of any Event of Default; and / or</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.3.3 levy by any Government Authority of any charge, Taxes or penalty in connection with regularising or perfecting any of the Financing Documents as may be required under applicable Law at any time during the currency of the Facility, or getting any of the Financing Documents admitted into evidence, or relying on any Financing Documents for proving any claim; and/or</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.3.4 the exercise of any of the rights by the Lender under this Agreement and any of the Financing Documents; and/or</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.3.5 any of the representations and warranties of the Borrower under the Financing Documents are found to be false or untrue or incorrect on a future date</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.4 Confidentiality</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Any information supplied by a Party to another Party pursuant hereto which is by its nature can reasonably be construed to be proprietary or confidential or is marked &ldquo;confidential&rdquo; (&ldquo;</span><strong>Confidential Information</strong><span style=\"font-weight: 400;\">&rdquo;) shall be kept confidential by the recipient unless or until compelled to disclose the same (i) by judicial or administrative process, or (ii) by law, or unless the same (iii) is in or is a part of public domain, or (iv) is required to be furnished to the bankers or investors or potential investors in the either Party, or (v) is required to be furnished to any Government Authority having jurisdiction over the recipient, or (vi) can be shown by the receiving Party to the reasonable satisfaction of the disclosing Party to have been known to the receiving Party prior to it being disclosed by the disclosing Party to the receiving Party, or (vii) subsequently comes lawfully into the possession of the receiving Party from a third party, and in such cases the confidentiality obligations shall cease to the extent required under the foregoing circumstances.&nbsp;</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.5 Amendments and Waivers</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">This Agreement (including the schedules, annexure and appendices hereto) may not be amended, supplemented or modified and no other Financing Document may be amended, supplemented or modified and no term or condition thereof may be waived without the written consent of the Parties to such Financing Document.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.6 Severability</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">Any provision of this Agreement or any other Financing Document which is prohibited or unenforceable shall be ineffective to the extent of prohibition or unenforceability but shall not invalidate the remaining provisions of this Agreement or any Financing Document.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.7 Survival</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.7.1 This Agreement shall be in force until all the Outstanding Amounts under this Agreement have been fully and irrevocably paid in accordance with the terms and provisions hereof.</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">15.7.2 The obligations of the Borrower under the Financing Documents will not be affected by:</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">a. any unenforceability, illegality or invalidity of any obligation of any Person under a Financing Document; or</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">b.the breach, frustration or non-fulfilment of any provisions of, or claim arising out of or in connection with a Financing Document.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.8 Right of Set-off</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">In addition to any rights now or hereafter granted under Applicable Law or otherwise, and not by way of limitation of any such rights, upon the occurrence and continuation of an Event of Default, the Lender is hereby authorised by the Borrower to, from time to time, without presentment, demand, protest or other notice of any kind to the Borrower, or to any other Person, set off and/or appropriate and/or apply any and all deposits (general or special) at any time held or owing by the Lender (including, without limitation, by any branches and agencies other than the lending office of Lender) to or for the credit or the account of the Borrower against and on account of the obligations and liabilities of the Borrower to the Lender under this Agreement or under any of the other Financing Documents.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.9 Notices</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">All notices and other communications provided at various places in this Agreement shall be in writing and (a) sent by hand delivery, or (b) prepaid registered post with acknowledgment due, or (c) by e-mail followed by prepaid registered post with acknowledgment due, at the address and/or email first above written. All such notices and communications shall be deemed to have been delivered effective: (i) if sent by email, when sent (provided the email enters the sent folder of the sender), (ii) if sent by prepaid registered post, 3 (three) Business Days after its dispatch.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.10 Effectiveness</span><strong>&nbsp;</strong></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">This Agreement shall become binding on the Parties hereto on and from the date hereof and shall be in force and effect till the Final Settlement Date.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.11 Entire Agreement</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">This Agreement and other Financing Documents shall represent the entire understanding of the Parties on the subject matter hereof and shall override all the previous understanding and agreement between the Parties hereto.</span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.12 No Discrimination</span></span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">The Borrower shall, at all times during the term of this Agreement, ensure that no fraudulent preference is given to other lender of the Borrower, both present and future, so as to defeat Lender&rsquo;s rights, either present and future under this Agreement or to fraudulently service the dues owed to other lenders in preference to the dues owed to the Lender or to wilfully act in or consent to any third party acting in a manner as would cause a Material Adverse Effect.</span><span style=\"font-weight: 400;\"><br /></span></p>\n" +
                    "<p><span style=\"text-decoration: underline;\"><span style=\"font-weight: 400;\">15.13 Governing Law and Arbitration</span></span></p>\n" +
                    "<div><strong>(a)</strong> This Agreement shall be governed by and construed in accordance with the laws of</div>\n" +
                    "<div>India.</div>\n" +
                    "<div>&nbsp;</div>\n" +
                    "<div><strong>(b)</strong> If any dispute which have arisen or which may arise between the parties with respect</div>\n" +
                    "<div>to the Agreement including its validity, interpretation, implementation or alleged</div>\n" +
                    "<div>breach of any provision of this Agreement (&ldquo;Dispute&rdquo;), the disputing parties hereto</div>\n" +
                    "<div>shall endeavour to settle such Dispute amicably. The attempt to bring about an</div>\n" +
                    "<div>amicable settlement shall be considered to have failed if not resolved within 15</div>\n" +
                    "<div>(fifteen) days from the date of the Dispute.</div>\n" +
                    "<div>&nbsp;</div>\n" +
                    "<div><strong>(c)</strong> Any Dispute which is not resolved pursuant to clause (b) within a</div>\n" +
                    "<div>period of 15 (fifteen) days from the day on which the Dispute arose and which a</div>\n" +
                    "<div>party wishes to have resolved, shall be referred to arbitration. The Lender shall</div>\n" +
                    "<div>appoint a sole arbitrator for the final resolution of such Dispute. The seat of the</div>\n" +
                    "<div>arbitration shall be New Delhi, India. The language of this arbitration shall be</div>\n" +
                    "<div>English.</div>\n" +
                    "<div>&nbsp;</div>\n" +
                    "<div><strong>(d)&nbsp;</strong>The arbitrator shall have the power to grant any legal or equitable remedy or relief</div>\n" +
                    "<div>available under applicable law, including injunctive relief (whether interim and/or</div>\n" +
                    "<div>final) and specific performance and any measures ordered by the arbitrator may be</div>\n" +
                    "<div>specifically enforced by any court of competent jurisdiction.</div>\n" +
                    "<p style=\"text-align: center;\"><strong>SCHEDULE I</strong></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p style=\"text-align: center;\"><strong>TERMS OF THE FACILITY</strong></p>\n" +
                    "<table  border=\"1\" cellspacing=\"0\">\n" +
                    "<tbody>\n" +
                    "<tr >\n" +
                    "<td style=\"width: 40px;\">\n" +
                    "<p><strong>S. NO.</strong></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px;\">\n" +
                    "<p style=\"text-align: center;\"><strong>PARTICULARS</strong></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px;\" colspan=\"2\">\n" +
                    "<p style=\"text-align: center;\"><strong>DETAILS</strong></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 40px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;1.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Date of Agreement</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;" + data.get("createdAt") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr >\n" +
                    "<td style=\"width: 40px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;2.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Place of Agreement</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;height: 36px;\">&nbsp;" + data.get("city") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 84px;\">\n" +
                    "<td style=\"width: 40px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;3.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Loan Agreement No.</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;" + data.get("externalLoanId") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 40px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;4.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Name of Borrower</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;" + data.get("merchantName") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 84px;\">\n" +
                    "<td style=\"width: 40px; height: 84px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;5.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 84px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Address of Borrower</span></p>\n" +
                    "<br />\n" +
                    "<p><span style=\"font-weight: 400;\">Email Address of Borrower</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 84px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;" + data.get("shopNumber") + data.get("streetAddress") + data.get("area") + data.get("landmark") + "</span></p>\n" +
                    "<br />\n" +
                    "<p><span span style=\"font-weight: 400;\">"+ data.get("email") +"</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 36px;\">\n" +
                    "<td style=\"width: 40px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;6.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Borrower's constitution</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 36px;\" colspan=\"2\"><br /><br />\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;Individual</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 49px;\">\n" +
                    "<td style=\"width: 40px; height: 49px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;7.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 49px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Purpose of the Facility/ Proposed utilization of the Facility</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 49px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;For&nbsp; General Use</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 36px;\">\n" +
                    "<td style=\"width: 40px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;8.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Amount of Loan</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 36px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;" + data.get("loanAmount") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 62px;\">\n" +
                    "<td style=\"width: 40px; height: 62px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;9.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 62px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Availability Period</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 62px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;</span>" + data.get("payableDays") + " days /" + data.get("tenure") + "</p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 36px;\">\n" +
                    "<td style=\"width: 40px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;10.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Business of the Borrower</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 36px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;" + data.get("businessCategory") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 36px;\">\n" +
                    "<td style=\"width: 40px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;11.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 36px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Penal Interest Rate</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 36px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;NIL</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 62px;\">\n" +
                    "<td style=\"width: 40px; height: 62px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;12.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 62px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Interest Rate</span></p>\n" +
                    "<br />\n" +
                    "<p><span style=\"font-weight: 400;\">(a)&nbsp;Interest chargeable&nbsp;</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 62px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;</span></p>\n" +
                    "<br />\n" +
                    "<p><span span style=\"font-weight: 400;\">&nbsp;" + data.get("interestRate") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 61px;\">\n" +
                    "<td style=\"width: 40px; height: 61px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;13.</p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 216px; height: 61px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Non-refundable Processing Fees /</span></p>\n" +
                    "<p><span style=\"font-weight: 400;\">service charge</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 20px; height: 61px;\" colspan=\"2\">\n" +
                    "<p><span style=\"font-weight: 400;\">\n" +
                    "&nbsp;NIL&nbsp;</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "</tbody>\n" +
                    "</table>\n" +
                    "<p><strong style=\"text-align: center;\">TABLE OF CHARGES</strong></p>\n" +
                    "<table style=\"width: 664px;\" border=\"1\" cellspacing=\"0\">\n" +
                    "<tbody>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 117px;\">\n" +
                    "<p><strong>Type of Charges</strong></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 54px;\">&nbsp;</td>\n" +
                    "<td style=\"width: 109px;\">\n" +
                    "<p><strong>Type of Charges</strong></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 42px;\">&nbsp;</td>\n" +
                    "<td style=\"width: 217px;\">\n" +
                    "<p><strong>Type of Charges</strong></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 105px;\">&nbsp;</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 117px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Late payment Charges</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 54px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">NIL</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 109px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Part Prepayment Charges</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 42px;\">NIL</td>\n" +
                    "<td style=\"width: 217px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Title Search Report Charges (Legal Charges)</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 105px;\">NIL</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 117px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Stamping Charges</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 54px;\">NIL</td>\n" +
                    "<td style=\"width: 109px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Processing Fee</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 42px;\">NIL</td>\n" +
                    "<td style=\"width: 217px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Other Charges</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 105px;\">NIL</td>\n" +
                    "</tr>\n" +
                    "</tbody>\n" +
                    "</table>\n" +
                    "<p style=\"text-align: center;\"><strong>SCHEDULE II</strong></p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p style=\"text-align: center;\"><strong>REPAYMENT SCHEDULE</strong></p>\n" +
                    "<table style=\"width: 666px;\" border=\"1\" cellspacing=\"0\">\n" +
                    "<tbody>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 40px;\">\n" +
                    "<p><strong>S. No</strong></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 173px;\">\n" +
                    "<p style=\"text-align: center;\"><strong>Particulars</strong></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 431px;\">\n" +
                    "<p style=\"text-align: center;\"><strong>Details</strong></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 40px;\">1.</td>\n" +
                    "<td style=\"width: 173px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Number of EDI</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 431px;\">&nbsp;" + data.get("payableDays") + "</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 40px;\">2.</td>\n" +
                    "<td style=\"width: 173px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Date of Commencement of EDI</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 431px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">&nbsp;" + data.get("createdAt") + "</span></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td style=\"width: 40px;\">3.</td>\n" +
                    "<td style=\"width: 173px;\">\n" +
                    "<p><span style=\"font-weight: 400;\">Mode of repayment</span></p>\n" +
                    "</td>\n" +
                    "<td style=\"width: 431px;\">&nbsp;QR Settlement</td>\n" +
                    "</tr>\n" +
                    "</tbody>\n" +
                    "</table>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<p>&nbsp;</p>\n" +
                    "<table style=\"height: 120px; width: 531px; margin-left: auto; margin-right: auto;\" border=\"1\" cellspacing=\"0\" cellpadding=\"0\">\n" +
                    "<tbody>\n" +
                    "<tr style=\"height: 27px;\">\n" +
                    "<td style=\"width: 189px; height: 27px; text-align: left;\"><strong>Applicant Name:</strong></td>\n" +
                    "<td style=\"width: 326px; text-align: center; height: 27px;\">&nbsp;<strong>" + data.get("merchantName") + "</strong>&nbsp;</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"text-align: center; height: 27px;\">\n" +
                    "<td style=\"width: 189px; height: 27px; text-align: left;\"><strong>Platform:</strong></td>\n" +
                    "<td style=\"width: 326px; height: 27px;\">&nbsp;<strong>Android</strong>&nbsp;</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"text-align: center; height: 26px;\">\n" +
                    "<td style=\"width: 189px; height: 26px; text-align: left;\"><strong>Browser :</strong></td>\n" +
                    "<td style=\"width: 326px; height: 26px;\">&nbsp;<strong>" + data.get("browserName") + "</strong>&nbsp;</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"text-align: center; height: 30px;\">\n" +
                    "<td style=\"width: 189px; height: 30px; text-align: left;\"><strong>IP Address:</strong></td>\n" +
                    "<td style=\"width: 326px; height: 30px;\">&nbsp;<strong>" + data.get("ipAddress") + "</strong>&nbsp;</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"text-align: center; height: 26px;\">\n" +
                    "<td style=\"width: 189px; height: 26px; text-align: left;\"><strong>Mobile Number for eSign:</strong></td>\n" +
                    "<td style=\"width: 326px; height: 26px;\">&nbsp;<strong>" + data.get("mobile") + "</strong>&nbsp;</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"text-align: center; height: 26px;\">\n" +
                    "<td style=\"width: 189px; height: 26px; text-align: left;\"><strong>Timestamp :</strong></td>\n" +
                    "<td style=\"width: 326px; height: 26px;\">&nbsp;<strong>" + data.get("createdAt") + "</strong>&nbsp;</td>\n" +
                    "</tr>\n" +
                    "</tbody>\n" +
                    "</table>\n" +
                    "<p style=\"text-align: left;\"><br /><strong>Date:&nbsp;" + data.get("createdAt") + "   &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;&nbsp; &nbsp; &nbsp;Place:&nbsp;"+data.get("city")+"</strong></p>";
        }else if ("LDC".equalsIgnoreCase(lender)){
            html = "<p class=\"p1\" style=\"text-align: center;\"><span class=\"s1\"><strong>Loan Details</strong></span></p>\n" +
                    "<p class=\"p2\" style=\"text-align: left;\">Loan ID:- "+data.get("externalLoanId")+" &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span style=\"text-alight:right;\">Date:-"+data.get("agreementAt")+"</span></p>\n" +
                    "<p class=\"p2\">Loan Amount (INR):-<span class=\"Apple-converted-space\">&nbsp; </span>"+data.get("loanAmount")+"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; Tenure (Months):-<span class=\"Apple-converted-space\" style=\"text-align:right;\">&nbsp; &nbsp; </span>"+data.get("tenure")+"</p>\n" +
                    "<p class=\"p2\">Flat Rate of Interest (% per month):-&nbsp;"+data.get("interestRate")+"</p>\n" +
                    "<p class=\"p2\">Flat Rate of Interest<span class=\"Apple-converted-space\">&nbsp; </span>(% per annum):-&nbsp;"+data.get("interestRateAnnum")+"</p>\n" +
                    "<p class=\"p2\">Amount of EDI :-"+data.get("edi")+"</p>\n" +
                    "<p class=\"p2\">BharatPe Registered Mobile Number:- "+data.get("mobile")+" &nbsp;&nbsp;&nbsp;Location:- "+data.get("location")+"</p>\n" +
                    "<p class=\"p2\">EDI Due Date &ndash; Everyday from Monday to Saturday from the successive day of disbursal</p>\n" +
                    "<p class=\"p2\">Shop/Business Address:-&nbsp;"+data.get("shopNumber")+data.get("streetAddress")+data.get("area")+data.get("landmark")+"</p>\n" +
                    "<p class=\"p2\">Landmark:-&nbsp; "+data.get("landmark")+" &nbsp;&nbsp;PIN:-&nbsp; "+data.get("pincode")+" &nbsp;&nbsp;City:-&nbsp; "+data.get("city")+" &nbsp;&nbsp;State:- &nbsp; "+data.get("state")+"</p>\n" +
                    "<p class=\"p3\">Email:- &nbsp;"+data.get("email")+"</p>\n" +
                    "<p class=\"p3\">Shop/ Business Phone Number:- &nbsp;"+data.get("mobile")+"</p>\n" +
                    "<p class=\"p3\">&nbsp;</p>\n" +
                    "<p class=\"p8\" style=\"text-align: center;\"><strong>LOAN AGREEMENT</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p9\">This Loan Agreement (the &ldquo;Agreement&rdquo;) is executed at Mumbai on "+data.get("agreementAt")+"</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p8\" style=\"text-align: center;\"><strong>BETWEEN</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p9\">The <strong>lender(s) arranged by Innofin Solutions Private Limited (&ldquo;LenDenClub&rdquo;), a P2P NBFC platform registered with RBI, hereinafter referred to as</strong> &ldquo;Lender&rdquo; and collectively referred to as the <strong>&ldquo;Lenders&rdquo;</strong> which expression shall, unless repugnant to the context thereof, mean and include their respective successors, legal representatives, heirs and permitted assigns),electronically agrees to this agreement, of the First Part</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p8\" style=\"text-align: center;\"><strong>AND</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p10\"><strong>Innofin Solutions Private Limited, </strong>a company incorporated under the provisions of the Companies Act, 2013 with corporate identity number [U74999MH2015PTC266499]<strong> having NBFC registration number as N-13.02267</strong> (hereinafter referred to as <strong>&ldquo;LenDenClub&rdquo;</strong> , which expression shall, unless excluded by or repugnant to the context or meaning thereof, include its successors and permitted assigns) of the Second Part;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p8\" style=\"text-align: center;\"><strong>AND</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p9\"><strong>"+data.get("merchantName")+"</strong> having PAN No. "+data.get("panNumber")+", and "+data.get("shopNumber")+data.get("streetAddress")+data.get("area")+data.get("landmark")+",  hereinafter referred to as &ldquo;<strong>the Borrower</strong>&rdquo; (which expression unless it be repugnant to the context or meaning thereof be deemed to mean and include his/her legal representative, assignee and administrator) of the Third Part.</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p9\">The Lender, Borrower and the LenDenClub are, wherever the context so requires, hereinafter collectively referred to as the &lsquo;<strong>Parties</strong>&rsquo; and individually as &lsquo;<strong>Party</strong>&rsquo;.</p>\n" +
                    "<p class=\"p6\" style=\"text-align: justify;\"><strong>A. WHEREAS, </strong>the Lender and the Borrower have come across each other for lending and borrowing unsecured loans through the LenDenClub having website as&nbsp;<a href=\"http://www.lendenclub.com\">www.lendenclub.com</a>;</p>\n" +
                    "<p style=\"text-align: justify;\"><strong>B</strong>. The Borrower is desirous of availing a loan of an aggregate amount of INR "+data.get("loanAmount")+" <span class=\"Apple-converted-space\">&nbsp; </span>(&ldquo;<strong>Loan Amount</strong>&rdquo;) from the Lender</p>\n" +
                    "<p style=\"text-align: justify;\"><strong>C.</strong>At the request of the Borrower and relying on representations and warranties, and covenants undertaken by the Borrower and subject to the terms and conditions contained herein, each of the individuals and/or institutions captured under Lender Group <strong>"+data.get("externalLoanId")+"</strong> hereby agrees to lend their proportion of the Loan Amount to the Borrower as captured in the information technology system of LenDenClub and the Borrower agrees to borrow from the Lender Group <span class=\"s3\"><strong>"+data.get("externalLoanId")+"</strong></span> the Loan Amount.</p>\n" +
                    "<p style=\"text-align: justify;\"><strong>D.</strong>WHEREAS, Lender desires for the LenDenClub,to mobilize the repayment amounts received from the Borrower and provided other services as detailed in this agreement and LenDenClub desires to provide such services, in accordance with the terms and conditions set forth in this Agreement.</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p9\"><strong>NOW, IN CONSIDERATION OF THE MUTUAL UNDERSTANDING AND OBLIGATIONS SET OUT IN THIS LOAN AGREEMENT, THE SUFFICIENCY OF WHICH IS HEREBY ACKNOWLEDGED, THE ABOVE PARTIES, INTENDING TO BE LEGALLY BOUND, AGREES AS FOLLOWS: -</strong></p>\n" +
                    "<p class=\"p6\"><strong>1.DEFINITIONS AND INTERPRETATIONS</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p9\">In this Loan Agreement, unless the context otherwise requires: (a) headings are for convenience only and shall not affect interpretation, (b) where a word or phrase is defined, other parts of speech and grammatical forms of that word or phrase shall have corresponding meanings, (c) words denoting any gender shall include all genders, (d) references to days, months and years are to calendar days, calendar months and calendar years respectively and (e) &ldquo;including&rdquo; and &ldquo;inter alia&rdquo; shall be deemed to be followed by &ldquo;without limitation&rdquo; or &ldquo;but not limited to&rdquo;.</p>\n" +
                    "<p class=\"p6\"><strong>2.DISBURSEMENT</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p9\">The Lender hereby agrees that platform can transfer respective Loan Amount to the Borrower. The date of disbursal of the loan shall be the Loan Date (&ldquo;Loan Date&rdquo;). In case, the borrower has availed a loan to purchase a product/service, the borrower has electronically authorised LenDenClub to transfer the Loan Amount to the person/entity/institution/business which has provided a product/services to the Borrower. The details of the bank account, where the Loan Amount has been transferred, after due authorisation of the Borrower is (\"Borrower's Bank Account\") as under:--</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<table class=\"t1\" style=\"width: 373px;\" border=\"1\" cellspacing=\"0\" cellpadding=\"0\">\n" +
                    "<tbody>\n" +
                    "<tr>\n" +
                    "<td class=\"td1\" style=\"width: 225px; text-align: center;\" valign=\"middle\">\n" +
                    "<p class=\"p9\"><strong>Bank Account</strong></p>\n" +
                    "</td>\n" +
                    "<td class=\"td1\" style=\"width: 342px; text-align: center;\" valign=\"middle\">\n" +
                    "<p class=\"p9\"><strong>"+data.get("accountNumber")+"</strong></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td class=\"td1\" style=\"width: 225px; text-align: center;\" valign=\"middle\">\n" +
                    "<p class=\"p9\"><strong>Account Holder Name</strong></p>\n" +
                    "</td>\n" +
                    "<td class=\"td1\" style=\"width: 342px; text-align: center;\" valign=\"middle\">\n" +
                    "<p class=\"p9\"><strong>"+data.get("beneficiaryName")+"</strong></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td class=\"td1\" style=\"width: 225px; text-align: center;\" valign=\"middle\">\n" +
                    "<p class=\"p9\"><strong>Type of Account</strong></p>\n" +
                    "</td>\n" +
                    "<td class=\"td1\" style=\"width: 342px; text-align: center;\" valign=\"middle\">\n" +
                    "<p class=\"p9\"><strong>SAVINGS</strong></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td class=\"td1\" style=\"width: 225px; text-align: center;\" valign=\"middle\">\n" +
                    "<p class=\"p9\"><strong>Bank Name</strong></p>\n" +
                    "</td>\n" +
                    "<td class=\"td1\" style=\"width: 342px; text-align: center;\" valign=\"middle\">\n" +
                    "<p class=\"p9\"><strong>"+data.get("bankName")+"</strong></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "<td class=\"td1\" style=\"width: 225px; text-align: center;\" valign=\"middle\">\n" +
                    "<p class=\"p9\"><strong>IFSC Code</strong></p>\n" +
                    "</td>\n" +
                    "<td class=\"td1\" style=\"width: 342px; text-align: center;\" valign=\"middle\">\n" +
                    "<p class=\"p9\"><strong>"+data.get("ifsc")+"</strong></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "</tbody>\n" +
                    "</table>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>3. RATE OF INTEREST</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">The borrower pays "+data.get("interestRate")+" % per month. Rate of Interest on this loan. Borrower pays EMI as per clause no. 6.</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p6\"><strong>4.RATE OF DEFAULT INTEREST &amp; DELAY CHARGES</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<ul class=\"ul1\">\n" +
                    "<li class=\"li12\">The Borrower do hereby agrees and confirms that in addition to the aforesaid Penal Interest, the Borrower shall be liable to pay delay charges as mentioned in Annexure 1</li>\n" +
                    "<li class=\"li12\">The Borrower do hereby agree and confirms that the Lender&rsquo;s right to recover Penal Interest and Delay Charges shall be without prejudice to Lender&rsquo;s other rights available as per this Loan Agreement.</li>\n" +
                    "<li class=\"li12\">The Borrower do hereby agree and confirms that his/her obligations to pay Penal Interest and Delay Charges shall not entitle the Borrower to set up the defense that no event of default as mentioned hereunder has occurred.</li>\n" +
                    "</ul>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p6\"><strong>5.DUE DATE OF FIRST &amp; SUBSEQUENT INSTALLMENTS</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p18\"><span class=\"s4\">The Parties do hereby agree and confirm that </span>the first installment shall become due and payable by the Borrower to the Lender from successive date of the loan disbursal. All subsequent installments shall become due and payable on a daily basis, from Monday to Saturday, as equated daily installments (EDI) till the repayment of Interest and Principal.<span class=\"Apple-converted-space\">&nbsp; &nbsp;</span></p>\n" +
                    "<p class=\"p19\"><strong>6.LOAN REPAYMENT SCHEDULE</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p9\">The Borrower do hereby covenants with the Lender(s) to repay to the Lender(s) the Loan Amount as equated daily installments (EDIs) of "+data.get("edi")+"<span class=\"Apple-converted-space\">&nbsp; </span>with interest payable in "+data.get("payableDays")+"<span class=\"Apple-converted-space\">&nbsp; </span>installments, in the following manner:</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<table class=\"t1\" style=\"width: 503px; height: 243px;\" border=\"1\" cellspacing=\"0\" cellpadding=\"0\">\n" +
                    "<tbody>\n" +
                    "<tr style=\"height: 35px;\">\n" +
                    "<td class=\"td1\" style=\"width: 165px; height: 35px;\" valign=\"middle\">\n" +
                    "<p class=\"p17\" style=\"text-align: center;\"><strong>Installment</strong></p>\n" +
                    "</td>\n" +
                    "<td class=\"td1\" style=\"width: 332px; height: 35px;\" valign=\"middle\">\n" +
                    "<p class=\"p17\" style=\"text-align: center;\"><strong>Total Amount(Rs.)</strong></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 35px;\">\n" +
                    "<td class=\"td1\" style=\"width: 165px; height: 35px;\" valign=\"middle\">\n" +
                    "<p class=\"p17\"><strong>1.&nbsp;"+data.get("payableDays")+" EDIs</strong></p>\n" +
                    "</td>\n" +
                    "<td class=\"td1\" style=\"width: 332px; height: 35px;\" valign=\"middle\">\n" +
                    "<p class=\"p20\">&nbsp; Rs."+data.get("edi")+" each = Rs."+data.get("repayment")+"</p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 35px;\">\n" +
                    "<td class=\"td1\" style=\"width: 165px; height: 35px;\" valign=\"middle\">\n" +
                    "<p class=\"p17\"><strong>2.</strong></p>\n" +
                    "</td>\n" +
                    "<td class=\"td1\" style=\"width: 332px; height: 35px;\" valign=\"middle\">\n" +
                    "<p class=\"p20\">&nbsp;</p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 35px;\">\n" +
                    "<td class=\"td1\" style=\"width: 165px; height: 35px;\" valign=\"middle\">\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "</td>\n" +
                    "<td class=\"td1\" style=\"width: 332px; height: 35px;\" valign=\"middle\">\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "</tbody>\n" +
                    "</table>\n" +
                    "<p class=\"p21\"><strong>7.MODE OF REPAYMENT OF LOAN</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<ul class=\"ul1\">\n" +
                    "<li class=\"li12\">The Borrower do hereby covenants with the Lender that for the purpose of repayment of the Loan Amount together with Interest and if applicable, Default Interest and Delay Charges, thereon by way of EDIs in the manner provided in Clause 6 above.</li>\n" +
                    "<li class=\"li12\">The Borrower hereby authorizes LenDenClub to disburse the Loan amount to the Bank Account mentioned in Clause 2</li>\n" +
                    "<li class=\"li12\">The Borrower hereby further authorizes to LenDenClub or its appointed agent to deduct money against due and payable EDIs from the Borrower&rsquo;s daily QR codes based settlement and credit it to LenDenClub repayment escrow account to facilitate the repayment of loan.</li>\n" +
                    "<li class=\"li12\">The borrower agrees to provide National Automated Clearing House Mandate (&ldquo;NACH Mandate&rdquo;) for the tenure of loan covering all the installments, in favour of the Lender or any other person or entity duly authorized by the Lender in this behalf or by handing over postdated duly signed cheques to LenDenClub for repayment of a loan instalment to the Lender.</li>\n" +
                    "<li class=\"li12\">The Borrower do hereby further covenants that without prior written consent of the Lender, the Borrower shall not close that bank account from which the NACH Mandate has been facilitated or from which the cheques has been issued, without prior intimation to Lender and making alternate arrangement for the payment of the balance Installments. Any instruction for closure of account without the consent of the Lender shall be deemed to be an event of default and consequence as setout in Clause 8 shall ensue.</li>\n" +
                    "</ul>\n" +
                    "<p class=\"p16\">&nbsp;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p6\"><strong>8.EVENTS OF DEFAULTS</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">8.1. The occurrence of the following events shall be an &ldquo;Event of Default&rdquo;:</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<ul class=\"ul1\">\n" +
                    "<li class=\"li12\">The Borrower fails to repay 60 sixty<span class=\"Apple-converted-space\">&nbsp; </span>consecutive EDIs<span class=\"Apple-converted-space\">&nbsp; </span>the due dates;</li>\n" +
                    "<li class=\"li12\">The Borrower has given instruction to the bank to the close the Borrower&rsquo;s Bank Account without the prior written of the Lender.</li>\n" +
                    "<li class=\"li12\">The Borrower commits breach of any terms and conditions set out in this Loan Agreement; and/or</li>\n" +
                    "<li class=\"li12\">If any attachment, distress execution or any other such process is initiated against the Borrower; and/or</li>\n" +
                    "<li class=\"li12\">If the Borrower ceases or threatens to cease or carry on his/her business or profession or employment.</li>\n" +
                    "</ul>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">8.2. Upon occurrence of an Event of Default, notwithstanding any elsewhere contained in this Loan Agreement, the outstanding amount (including the Interest, Default Interest and Delay Charges) as on the date of Event of Default shall immediately become due and payable and the Lender shall be entitled to take all steps /actions available to him under law/equity or otherwise to recover the amount.</p>\n" +
                    "<p class=\"p6\"><strong>9.APPROPRIATION OF PAYMENTS</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">The Borrower hereby agrees and confirms that any payment made by him/her to the Lender under and/or in terms of this Loan Agreement shall be appropriated in the following manner:-</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>Firstly, </strong>towards the costs, fees, expenses and other charges, if any, which the Lender may have to incur for the recovery of amounts payable by the Borrower under this Loan Agreement;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>Secondly, </strong>towards the payment of Default Interest and Delay Charges, due and payable under this Loan Agreement;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>Thirdly, </strong>towards the payment of Interest, due and payable on the Loan Amount;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>Lastly, </strong>towards the payment of installment of the Principal Loan Amount.</p>\n" +
                    "<p class=\"p6\"><strong>10.PREPAYMENT OF LOAN</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">The Lender hereby agrees and confirms that the Borrower shall have the option to repay in full or part before the due dates of<span class=\"Apple-converted-space\">&nbsp; </span>any or all of the EDIs installments, which may be outstanding at that point of time, provided that the Borrower shall remain liable to pay the interest on the Loan Amount outstanding till the date of payment.</p>\n" +
                    "<p class=\"p12\">The lender hereby agrees and confirms that the borrower shall have the option to prepay the loan in full or part at anytime.</p>\n" +
                    "<p class=\"p6\"><strong>11.SCOPE OF SERVICES OF LenDenClub</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">Each of the Lender has electronically authorized the LenDenClub to undertake the following activities on its behalf:</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<ul class=\"ul1\">\n" +
                    "<li class=\"li12\">To facilitate mobilization of repayment amounts received from the Borrower account to Lender account;</li>\n" +
                    "<li class=\"li12\">To initiate legal action against the Borrower in case of default in payment or non- payment by the Borrower for more than 30 days as and when need arises and on such fees as may be mutually agreed between the Collection LenDenClub and the Lender.</li>\n" +
                    "</ul>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">For avoidance of doubt, it is hereby clarified that the services provided by the LenDenClub under this Agreement are limited to point (a) and point (b) above, and under no circumstances LenDenClub will be responsible for collection of money and/or towards payment of any EDI or delay or penal charges on behalf of Borrower. The Lender and Borrower agree, understand and acknowledge that payment of any EDI or delay or penal charges is always the sole responsibility of the Borrower.</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p6\"><strong>12.REPRESENTATIONS AND WARRANTIES OF THE BORROWER</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">The Borrower do hereby represents and warrants as under:</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<ul class=\"ul1\">\n" +
                    "<li class=\"li12\">All information which has been given by Borrower to the Lender with respect to himself is true and accurate in all respects.</li>\n" +
                    "<li class=\"li12\">That the Borrower shall utilized the Loan Amount for the purpose of ADVANCE SALARY and for no other purpose;</li>\n" +
                    "<li class=\"li12\">That there are no any circumstances of whatsoever nature that would render the transaction contemplated by this Loan Agreement, void or voidable at the option of the Borrower, under the provisions of any Law in force in India;</li>\n" +
                    "<li class=\"li12\">That the Borrower is not a party to any litigation of a material character and that the Borrower is not aware of any fact likely to give rise to any litigation or to any material claims against the Borrower;</li>\n" +
                    "<li class=\"li12\">that there is no action, suit proceeding, order or investigation pending and/or continuing or to the knowledge of the Borrower initiated by or against the Borrower before any Court of Law;</li>\n" +
                    "</ul>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p6\"><strong>13.REPRESENTATIONS AND WARRANTIES OF THE LENDER</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">The Lender do hereby represents and warrants as under:</p>\n" +
                    "<ul class=\"ul1\">\n" +
                    "<li class=\"li12\">That the Lender has adequate legal capacity to enter into this Loan Agreement and perform his or her obligations hereunder.</li>\n" +
                    "<li class=\"li12\">That the Lender is not restricted to enter into this Loan Agreement by any Law or any other Loan Agreement;</li>\n" +
                    "<li class=\"li12\">That this Loan Agreement and all documents required to be executed under and/or in relation to this Loan Agreement constitute and will constitute valid and binding obligations of the Lender enforceable in accordance with law.</li>\n" +
                    "</ul>\n" +
                    "<p class=\"p6\"><strong>14.POWERS CONFERRED BY THE BORROWER IN FAVOUR OF THE LENDER</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">The Borrower hereby irrevocably empowers and appoints the Lender to exercise any rights mentioned in this Loan Agreement and/or exercisable by the Lender on behalf of the Borrower and also to act and represent the Borrower in such of the acts expressed to be necessary for the purpose of carrying out the terms and conditions of this Loan Agreement and to represent the Borrower before all authorities including the Borrower&rsquo;s employer, banker, etc. and the Borrower undertakes to furnish all such information, statements, documents and the papers to be submitted or furnished or to be filed before any authority.</p>\n" +
                    "<p class=\"p6\"><strong>15.COVENANTS AND UNDERTAKINGS OF THE BORROWER</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">The Borrower do hereby agree, covenants and undertakes as under</p>\n" +
                    "<ul class=\"ul1\">\n" +
                    "<li class=\"li12\">That the Borrower shall pay and bear all expenses including stamp Duty and registration charges on actual basis and other charges and expenses which may be incurred in preparation of this Loan Agreement and/or any other related or incidental documents as may be required to be executed in future in connection with the disbursal of the loan to the Borrower by the Lender.</li>\n" +
                    "<li class=\"li12\">That in case, if the Lender incurs any legal or other charges or expenses for the recovery of any part of the Loan Amount together with Interest, Default Interest and Delay Charges, then in that event the Borrower shall also be liable to pay the amounts paid by the Lender for such purposes along with interest on such amounts at the rate of Default Interest mentioned hereinabove, from the date of receipt of demand notice by the Borrower.</li>\n" +
                    "<li class=\"li12\">That the statement of account forward by the Lender or by any other person or entity duly authorized by the Lender in that behalf to the Borrower, shall be accepted by the Borrower as the conclusive proof of the correctness of the Lender&rsquo;s claim due and payable by the Borrower on that particular date.</li>\n" +
                    "<li class=\"li12\">That the Lender as well as the LenDenClub shall be entitled to disclose and furnish to any of the Credit Information Agencies duly authorized by the RBI in that behalf, all such data and information describing the manner in which the Borrower is performing his/her obligations arising under this Loan Agreement and the Borrower shall not be entitled to raise any objection/s in respect thereof.</li>\n" +
                    "<li class=\"li12\">That the data and the information so disclosed and furnished by the Lender as well as by the LenDenClub<span class=\"Apple-converted-space\">&nbsp; </span>to any of the Credit Information Agencies may be used or processed by such Agencies in the manner as they may deem fit and proper to test the creditworthiness of the Borrower;</li>\n" +
                    "<li class=\"li12\">That the rights, powers and remedies given to the Lender by this Loan Agreement shall be in addition to all rights, powers and remedies given to the Lender by virtue of any other statute or rule of law.</li>\n" +
                    "<li class=\"li12\">That the Lender at his/her absolute discretion may at any point of time set-off any of the obligation/s and/or the part of the obligation/s of the Borrower arising under this Loan Agreement.</li>\n" +
                    "<li class=\"li12\">That at the absolute discretion of the Lender, the Lender shall be entitled to enforce this Loan Agreement himself/herself personally or through any other person or entity duly authorized in writing by the Lender in that behalf and in that event all the covenants and undertakings given by the Borrower to the Lender shall be deemed to have been duly given by the Borrower to such other person or entity who is duly authorized in writing by the Lender;</li>\n" +
                    "<li class=\"li12\">That the Borrower shall comply with all covenants, terms, conditions stipulated in this Loan Agreement and shall fully indemnify and keep indemnified the Lender from and against all actions, proceedings, liabilities, claims, demands, loses, damages, costs, charges and expenses whatsoever in respect of or in relation to or arising out all obligations and liabilities of the Borrower under this Loan Agreement.</li>\n" +
                    "</ul>\n" +
                    "<p class=\"p6\"><strong>16.JOINT COVENANTS OF THE LENDER &amp; THE BORROWER</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<ul class=\"ul1\">\n" +
                    "<li class=\"li12\">The Parties do hereby covenants with each other that LenDenClub<span class=\"Apple-converted-space\">&nbsp; </span>has only facilitated their virtual meeting and as such the LenDenClub is not obliged under this Loan Agreement. The Loan Agreement has been executed on a principal to principal basis between the two Parties. However, it is clarified that in case if the need so arises, any of the Parties may approach the LenDenClub and duly authorize the LenDenClub in writing to take necessary steps for the full and faithful compliance of this Loan Agreement by the other party and in such an event, the defaulting party shall not be entitled to take any objections in respect thereof.</li>\n" +
                    "<li class=\"li12\">That the information, representations and warranties furnished by the Parties from time to time on the website of LenDenClub and as mentioned in this Loan Agreement are true and correct and as such no part of the information, representations and warranties furnished by the Parties are incorrect.</li>\n" +
                    "<li class=\"li12\">That the Parties have read and understood all the terms and conditions, privacy policy and other material available on the website of LenDenClub and do hereby covenant and undertake to unconditionally abide by the same, without raising any defense of whatsoever nature in respect thereof.</li>\n" +
                    "</ul>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p22\"><strong>17.<span class=\"Apple-converted-space\">&nbsp; &nbsp; &nbsp; </span>INDEMNITY</strong></p>\n" +
                    "<p class=\"p23\">Without prejudice to and in addition to other provisions contained in the Financing Documents, the Borrower hereby agrees to indemnify the Lender/LenDenClub and its directors, officers, representatives and agents against any losses, liabilities, claims, damages or the like (including, without limitation, reasonable attorneys' fees and expenses) which may be sustained or incurred by any of them as a result of, or in connection with, or arising out of:</p>\n" +
                    "<ul class=\"ul1\">\n" +
                    "<li class=\"li24\">the Borrower failing to comply with the provisions of any Financing Documents and applicable Laws; and / or the occurrence of any Event of Default; and / or</li>\n" +
                    "<li class=\"li24\">levy by any Government Authority of any charge, Taxes or penalty in connection with regularising or perfecting any of the Financing Documents as may be required under applicable Law at any time during the currency of the Facility, or getting any of the Financing Documents admitted into evidence, or relying on any Financing Documents for proving any claim; and/or</li>\n" +
                    "<li class=\"li24\">the exercise of any of the rights by the Lender under this Agreement and any of the Financing Documents; and/or</li>\n" +
                    "<li class=\"li22\">any of the representations and warranties of the Borrower under the Financing Documents are found to be false or untrue or incorrect on a future date..</li>\n" +
                    "</ul>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p22\"><strong>18.<span class=\"Apple-converted-space\">&nbsp; &nbsp; &nbsp; </span>PROVISIONS REGARDING TERMINATION/CANCELLATION OF THIS LOAN AGREEMENT</strong></p>\n" +
                    "<p class=\"p12\">Notwithstanding anything contained in this Loan Agreement, the Lender may at his/her option and without necessity of any demand or notice to the Borrower, all of which are hereby expressly waived off by the Borrower, terminate this Loan Agreement upon happening of any of the following events and thereupon all the amounts due and outstanding by the Borrower to the Lender on such day shall at once become due and payable by the Borrower to the Lender, irrespective of any agreed maturity date:</p>\n" +
                    "<ul class=\"ul1\">\n" +
                    "<li class=\"li12\">If the Borrower makes default in payment of 60 installments, due and payable to the Lender;</li>\n" +
                    "<li class=\"li12\">If any event or circumstances has occurred or may arise which is prejudicial to or impairs or imperils or depreciates or jeopardizes or is likely to prejudice, imperil or depreciate or jeopardize any of the rights of the Lender arising under this Loan Agreement;</li>\n" +
                    "<li class=\"li12\">If any information furnished or representations made by the Borrower is found to be false, incorrect or incomplete in material particulars; and</li>\n" +
                    "<li class=\"li12\">If the Borrower is continuously, for a period of 15 days not traceable and/or not responding to the communications made by the Lender and/or the LenDenClub.</li>\n" +
                    "</ul>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>19. <span class=\"Apple-converted-space\">&nbsp; &nbsp; </span>ALTERATION OR MODIFICATION</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">The Parties shall be at liberty to mutually amend or alter or modify any of the terms and conditions of this Loan Agreement and in particular to defer, postpone or revise the repayment of the Loan Amount, Interest, Penal Interest and Delay Charges and/or any other monies which may become due and payable by the Borrower to the Lender, including any increase or decrease in the rate of interest. However, it is clarified that all such amendment, alteration or modification must be writing and duly signed by both the Parties.</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>20. <span class=\"Apple-converted-space\">&nbsp; &nbsp; </span>WAIVER</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">Any forbearance or failure or delay by the Lender in exercising any right, power or remedy hereunder shall not be deemed to be waiver of such right, power or remedy and any single or partial exercise of any right, power or remedy hereunder shall not preclude the further exercise thereof and every right, power and remedy of the Lender shall continue to be in full force and effect until such right, power and remedy is specifically waived by an instrument in writing executed by the Lender.</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>21.<span class=\"Apple-converted-space\">&nbsp; &nbsp; &nbsp; </span>SEVERABILITY</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p24\">If any provision or any part thereof of this Loan Agreement is determined to be illegal, invalid or unenforceable for any reason, such illegality, invalidity or unenforceability shall attach only to such provision or the applicable part of such provision and the remaining part of such provision and all other provisions of this Loan Agreement shall continue to remain in full force and effect.</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>22.<span class=\"Apple-converted-space\">&nbsp; &nbsp; &nbsp; </span>GOVERNING LAW/JURISDICTION</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">This Loan Agreement shall be governed by and construed in accordance with the Laws of Maharashtra, India and any dispute between the Parties relating to or arising out of this Loan Agreement shall be subject to the exclusive jurisdiction of Courts at Mumbai, India.</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>23.<span class=\"Apple-converted-space\">&nbsp; &nbsp; &nbsp; </span>ENTIRE UNDERSTANDING</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">This Loan Agreement constitutes the whole Agreement between the Parties and supersedes any previous arrangement between the Parties in relation to the matters dealt with in this Loan Agreement, provided that this clause shall not exclude any liability for (or remedy in respect of) fraudulent misrepresentation.</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>24.<span class=\"Apple-converted-space\">&nbsp; &nbsp; &nbsp; </span>SURVIVAL</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">The Clauses of this Agreement which by their nature survive termination shall survive the expiry or termination of this Agreement.</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>25.<span class=\"Apple-converted-space\">&nbsp; &nbsp; </span>TIME IS THE ESSENCE</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">The Parties hereby agree that time is the essence with respect to all dates and periods mentioned in this Loan Agreement (as may be modified/extended wherever permitted under this Loan Agreement), for performance of their respective obligations under this Loan Agreement.</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>26. <span class=\"Apple-converted-space\">&nbsp; &nbsp; &nbsp; </span>NOTICES</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">Any notice or demand to be given under this Loan Agreement shall be in writing; and shall be deemed to have been duly given if sent by email or by a courier service or registered A. D. or personally delivered. Each notice or demand shall be addressed to the other Parties at the address mentioned above and a notice or demand so given or made shall be deemed to be given or made on the day it was so left or; as the case may be, two business days following date on which it was so posted and shall be effectual notwithstanding that the same may be returned undelivered and notwithstanding the Borrower&rsquo;s change of address.</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<div><strong>\n" +
                    "<p class=\"p12\"><strong>26.<span class=\"Apple-converted-space\">&nbsp; &nbsp; </span>Governing Law and Arbitration</strong></p>\n" +
                    "<div><strong>(a)</strong> This Agreement shall be governed by and construed in accordance with the laws of</div>\n" +
                    "<div>India.</div>\n" +
                    "<div>&nbsp;</div>\n" +
                    "<div><strong>(b)</strong> If any dispute which have arisen or which may arise between the parties with respect</div>\n" +
                    "<div>to the Agreement including its validity, interpretation, implementation or alleged</div>\n" +
                    "<div>breach of any provision of this Agreement (&ldquo;Dispute&rdquo;), the disputing parties hereto</div>\n" +
                    "<div>shall endeavour to settle such Dispute amicably. The attempt to bring about an</div>\n" +
                    "<div>amicable settlement shall be considered to have failed if not resolved within 15</div>\n" +
                    "<div>(fifteen) days from the date of the Dispute.</div>\n" +
                    "<div>&nbsp;</div>\n" +
                    "<div><strong>(c)</strong> Any Dispute which is not resolved pursuant to clause (b) within a</div>\n" +
                    "<div>period of 15 (fifteen) days from the day on which the Dispute arose and which a</div>\n" +
                    "<div>party wishes to have resolved, shall be referred to arbitration. The Lender shall</div>\n" +
                    "<div>appoint a sole arbitrator for the final resolution of such Dispute. The seat of the</div>\n" +
                    "<div>arbitration shall be New Delhi, India. The language of this arbitration shall be</div>\n" +
                    "<div>English.</div>\n" +
                    "<div>&nbsp;</div>\n" +
                    "<div><strong>(d)&nbsp;</strong>The arbitrator shall have the power to grant any legal or equitable remedy or relief</div>\n" +
                    "<div>available under applicable law, including injunctive relief (whether interim and/or</div>\n" +
                    "<div>final) and specific performance and any measures ordered by the arbitrator may be</div>\n" +
                    "<div>specifically enforced by any court of competent jurisdiction.</div>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\">THE PARTIES HERETO HAVE EXECUTED THESE PRESENTS ON THE DAY, MONTH AND YEAR FIRST HEREINABOVE WRITTEN. <strong>ELECTRONICALLY SIGNED</strong> by,</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p25\"><strong>Borrower:-&nbsp; "+data.get("merchantName")+"</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p25\">Date:-&nbsp; "+data.get("createdAt")+"</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p25\">Location:- &nbsp; "+data.get("location")+"</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p25\">IP:-&nbsp; "+data.get("ipAddress")+"</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p25\"><strong>Lender(s)</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p25\">LenderGroup ID:-&nbsp; "+data.get("externalLoanId")+"</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p25\"><strong>Date:-&nbsp; "+data.get("agreementAt")+"</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p25\"><strong>Agreed by LenDenClub on behalf of Lender(s) based on Electronic Authorization given by Lender(s)</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p25\"><strong>LenDenClub:</strong> <strong>Innofin Solutions Pvt. Ltd</strong></p>\n" +
                    "<p class=\"p26\">&nbsp;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p25\">Date: &nbsp; "+data.get("agreementAt")+"</p>\n" +
                    "<p class=\"p26\">&nbsp;</p>\n" +
                    "<p class=\"p8\" style=\"text-align: center;\"><strong>Annexure 1 : Charges applicable to user</strong></p>\n" +
                    "<p class=\"p27\" style=\"text-align: center;\">&nbsp;</p>\n" +
                    "<p class=\"p27\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>Details of the charges applicable in case &lt;Borrower Name&gt; would be as following:</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<table class=\"t1\" style=\"width: 313px;\" border=\"1\" cellspacing=\"0\" cellpadding=\"0\">\n" +
                    "<tbody>\n" +
                    "<tr style=\"height: 35px;\">\n" +
                    "<td class=\"td1\" style=\"width: 165px; height: 35px;\" valign=\"middle\">\n" +
                    "<p class=\"p12\" style=\"text-align: center;\"><strong>Type of Charges</strong></p>\n" +
                    "</td>\n" +
                    "<td class=\"td1\" style=\"width: 142px; height: 35px;\" valign=\"middle\">\n" +
                    "<p class=\"p12\" style=\"text-align: center;\"><strong>Applicable charge</strong></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 35px;\">\n" +
                    "<td class=\"td1\" style=\"width: 165px; height: 35px;\" valign=\"middle\">\n" +
                    "<p class=\"p12\"><strong>Delay Charges </strong></p>\n" +
                    "</td>\n" +
                    "<td class=\"td1\" style=\"width: 142px; height: 35px;\" valign=\"middle\">\n" +
                    "<p class=\"p12\" style=\"text-align: center;\"><strong>0 %</strong></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "<tr style=\"height: 35px;\">\n" +
                    "<td class=\"td1\" style=\"width: 165px; height: 35px;\" valign=\"middle\">\n" +
                    "<p class=\"p12\"><strong>Penal Interest Charge</strong></p>\n" +
                    "</td>\n" +
                    "<td class=\"td1\" style=\"width: 142px; height: 35px;\" valign=\"middle\">\n" +
                    "<p class=\"p12\" style=\"text-align: center;\"><strong>0 %</strong></p>\n" +
                    "</td>\n" +
                    "</tr>\n" +
                    "</tbody>\n" +
                    "</table>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>Applicable Charges will be divided among Lenders and the LenDenClub.</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>In addition to the above charges, Borrower will also be liable to pay visiting charges to the LenDenClub if any of the LenDenClub representative visits Borrower&rsquo;s home or office to collect EMI or when Borrower is not contactable through his registered email address or mobile number.</strong></p>\n" +
                    "<p class=\"p27\">&nbsp;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>Charges conveyed &amp; accepted by:</strong></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><span class=\"s1\">Borrower</span></p>\n" +
                    "<p class=\"p6\">&nbsp;</p>\n" +
                    "<p class=\"p12\"><strong>"+data.get("merchantName")+"</strong></p>";
        }
        return html;
    }

    private int getDPD(LendingPaymentSchedule lendingPaymentSchedule) {
        if (lendingPaymentSchedule == null || lendingPaymentSchedule.getDueAmount() == null) {
            return 0;
        }
        return (int) (lendingPaymentSchedule.getDueAmount() / lendingPaymentSchedule.getEdiAmount());
    }
}
