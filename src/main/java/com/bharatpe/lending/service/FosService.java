package com.bharatpe.lending.service;


import com.bharatpe.cache.service.LendingCache;
import com.bharatpe.common.dao.*;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Handler.EnachHandler;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.Handler.PartnersApiHandler;
import com.bharatpe.lending.common.dao.LoanAttributionDao;
import com.bharatpe.lending.common.entity.LoanAttribution;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.BharatPeEnachResponseDTO;
import com.bharatpe.lending.common.dto.LendingNachBankResponseDTO;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.ApplicationStage;
import com.bharatpe.lending.common.enums.PincodeColor;
import com.bharatpe.lending.common.enums.RejectionStage;
import com.bharatpe.lending.common.service.merchant.constants.Constants;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
//import com.bharatpe.lending.common.slave.dao.BankListDaoSlave;
//import com.bharatpe.lending.common.slave.dao.IfscDaoSlave;
//import com.bharatpe.lending.common.slave.dao.MerchantInferredLocationDaoSlave;
//import com.bharatpe.lending.common.slave.entity.MerchantInferredLocationSlave;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.common.util.EasyLoanUtil;
import com.bharatpe.lending.dao.*;
import com.bharatpe.lending.dto.*;
import com.bharatpe.lending.handlers.MerchantSummaryExceptionHandler;
import com.bharatpe.lending.loanV2.service.LoanDetailsServiceV2;
import com.bharatpe.lending.util.LoanUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;

@Service
public class FosService {
    private Logger logger = LoggerFactory.getLogger(FosService.class);

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    EnachHandler enachHandler;

    @Autowired
    ExperianDao experianDao;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

//    @Autowired
//    LendingCpvDetailsDao lendingCpvDetailsDao;

//    @Autowired
//    IfscDaoSlave ifscDaoSlave;

//    @Autowired
//    BankListDaoSlave bankListDaoSlave;

    @Autowired
    APIGatewayService apiGatewayService;

//    @Autowired
//    LendingPennydropDao lendingPennydropDao;

//    @Autowired
//    DocumentsIdProofDao documentsIdProofDao;

//    @Autowired
//    DocKycDetailsDaoMaster docKycDetailsDaoMaster;

//    @Autowired
//    MerchantInferredLocationDaoSlave merchantInferredLocationDaoSlave;

//    @Autowired
//    MerchantDao merchantDao;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LoanAttributionDao loanAttributionDao;

    @Autowired
    LoanUtil loanUtil;

    @Value("${fos.task.enabled:false}")
    Boolean isFosTaskEnabled;

    @Autowired
    LendingPincodesDao lendingPincodesDao;

    @Autowired
    LendingBlockedPancardDao lendingBlockedPancardDao;

    @Autowired
    DateTimeUtil dateTimeUtil;

    @Autowired
    EasyLoanUtil easyLoanUtil;

    @Autowired
    LendingRiskVariablesDao lendingRiskVariablesDao;

    @Autowired
    PartnersApiHandler partnersApiHandler;

    @Autowired
    LoanDetailsServiceV2 loanDetailsServiceV2;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    LendingCache lendingCache;

    @Autowired
    MerchantSummaryHandler merchantSummaryHandler;
    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

    public ResponseDTO fosLoan(Long merchantId) {
        ResponseDTO responseDTO = new ResponseDTO(true, null, null, null);
        Map<String, Object> data = new HashMap<>();
        data.put("rejected", Boolean.FALSE);
        data.put("merchantId", merchantId.toString());
        data.put("activeLoan", Boolean.FALSE);
        data.put("eligible", Boolean.FALSE);
        data.put("experian", Boolean.TRUE);
        data.put("applicationPending", Boolean.FALSE);
        try {
            Experian experian = experianDao.getByMerchantId(merchantId);
            if (experian == null) {
                data.put("message", "Merchant Experian Not Pulled");
                data.put("experian", Boolean.FALSE);
                responseDTO.setData(data);
                return responseDTO;
            }
            String reason = experian.getReason();
            if ("ENACH".equalsIgnoreCase(reason)) {
                reason = "Merchant Bank A/C does Not Allow Enach.";
            } else if ("OGL".equalsIgnoreCase(reason)) {
                reason = "Entered PIN Code Area is not Serviceable right now.";
            } else {
                reason = "Keep Transacting With BharatPe To Become Eligible.";
            }

            if (experian.getRejected()) {
                data.put("message", reason);
                data.put("rejected", Boolean.TRUE);
                data.put("eligible", Boolean.FALSE);
                responseDTO.setData(data);
                return responseDTO;
            }

            if (experian.getReason() != null) {
                data.put("message", reason);
                data.put("rejected", Boolean.TRUE);
                responseDTO.setData(data);
                return responseDTO;
            }
            EligibleLoan eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
            LendingApplication lendingApplication = lendingApplicationDao.findBymerchantId(merchantId);
            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId, "ACTIVE");
            logger.info("Payment Schedule:{}", lendingPaymentSchedule);
            if (lendingPaymentSchedule != null) {
                data.put("message", "Merchant Has a Active Loan.");
                data.put("activeLoan", Boolean.TRUE);
                responseDTO.setData(data);
                return responseDTO;
            }
            if (eligibleLoan == null && lendingApplication == null) {
                data.put("message", "Merchant Not Eligible For Loan.");
                data.put("eligible", Boolean.FALSE);
                responseDTO.setData(data);
                return responseDTO;
            }
            if (!isFosTaskEnabled) {
                data.put("message", "Merchant Not Eligible For Loan.");
                data.put("eligible", Boolean.FALSE);
                responseDTO.setData(data);
                return responseDTO;
            }
            if (lendingApplication == null) {
                data.put("message", "Merchant is Eligible For Loan.");
                data.put("eligible", Boolean.TRUE);
                responseDTO.setData(data);
                return responseDTO;
            } else {
                data.put("applicationPending", Boolean.TRUE);
                data.put("applicationRejected", Boolean.FALSE);
                data.put("eligible", Boolean.TRUE);
                data.put("nachRequired", Boolean.FALSE);
                data.put("created_at", lendingApplication.getCreatedAt().toString());
                data.put("loanType", lendingApplication.getLoanType());
                data.put("loanAmount", lendingApplication.getLoanAmount());
                data.put("loanId", lendingApplication.getExternalLoanId());
                data.put("nachStatus", "APPROVED".equals(lendingApplication.getNachStatus()) ? "APPROVED" : "PENDING");
                String loanType = lendingApplication.getLoanType();

                if ("draft".equals(lendingApplication.getStatus())) {
                    data.put("message", "Application Is Draft Mode.");
                    responseDTO.setData(data);
                    return responseDTO;
                }

                if ("approved".equals(lendingApplication.getStatus())) {
                    data.put("message", "Merchant Application Is Approved State.");
                    data.put("agreement_at", lendingApplication.getAgreementAt().toString());
                    responseDTO.setData(data);
                    return responseDTO;
                }

                if ("pending_verification".equals(lendingApplication.getStatus())) {
                    data.put("message", "Merchant Loan Application Is Pending Verification State.");
                    data.put("agreement_at", lendingApplication.getAgreementAt().toString());
                    if (("NTB".equals(loanType) || "OGL".equals(loanType) || "BHARAT_SWIPE".equals(loanType) || "NTB_SMS_1".equals(loanType)) && !"APPROVED".equals(lendingApplication.getNachStatus())) {
                        data.put("message", "Please Complete Enach For Further Process Application.");
                        data.put("nachRequired", Boolean.TRUE);
                    }
                    responseDTO.setData(data);
                    return responseDTO;
                } else {
                    data.put("message", "Merchant Loan Application Is Rejected State.");
                    data.put("applicationPending", Boolean.FALSE);
                    data.put("applicationRejected", Boolean.TRUE);
                    responseDTO.setData(data);
                    return responseDTO;
                }
            }
        } catch (Exception ex) {
            logger.error("Error Fos Loan Details API", ex);
            return responseDTO;
        }
    }

//    public ResponseDTO fosnewLoan(Long merchantId) {
//        ResponseDTO responseDTO = new ResponseDTO(true, null, null, null);
//        Map<String, Object> data = new HashMap<>();
//        Map<String, Object> loanData = new HashMap<>();
//        Map<String, Object> details = new HashMap<>();
//        try {
//            LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findByMerchantIdAndStatus(merchantId, "ACTIVE");
//            if (lendingPaymentSchedule != null) {
//                loanData.put("activeLoan", Boolean.TRUE);
//                loanData.put("eligible", Boolean.FALSE);
//                loanData.put("color", "#ED6A5B");
//                loanData.put("header", "Loan Already Active");
//                loanData.put("message", "Merchant Already have an Active Loan.");
//                data.put("loan_data", loanData);
//                data.put("task_enable", Boolean.FALSE);
//                responseDTO.setData(data);
//                return responseDTO;
//            }
//            Experian experian = experianDao.getByMerchantId(merchantId);
//            if (experian == null) {
//                loanData.put("experian", Boolean.FALSE);
//                loanData.put("eligible", Boolean.FALSE);
//                loanData.put("color", "#EAA003");
//                loanData.put("header", "Merchant Not Eligible");
//                loanData.put("message", "Please Ask Merchant to Enter PAN/PIN");
//                data.put("loan_data", loanData);
//                data.put("task_enable", Boolean.TRUE);
//                responseDTO.setData(data);
//                return responseDTO;
//            }
//            if (experian.getBpScore() != null && experian.getBpScore() < 2D) {
//                loanData.put("experian", Boolean.TRUE);
//                loanData.put("eligible", Boolean.FALSE);
//                loanData.put("color", "#EAA003");
//                loanData.put("header", "Merchant Not Eligible");
//                loanData.put("message", "Task Not Enable");
//                data.put("loan_data", loanData);
//                data.put("task_enable", Boolean.FALSE);
//                responseDTO.setData(data);
//                return responseDTO;
//            }
//            String reason = experian.getReason();
//            if ("ENACH".equalsIgnoreCase(reason)) {
//                reason = "Merchant Bank A/C does Not Allow Enach.";
//            } else if ("OGL".equalsIgnoreCase(reason)) {
//                reason = "Entered PIN Code is not Serviceable right now";
//            } else {
//                reason = "Keep Transacting With BharatPe To Become Eligible";
//            }
//            if (experian.getRejected() || experian.getReason() != null) {
//                loanData.put("experian", Boolean.TRUE);
//                loanData.put("eligible", Boolean.FALSE);
//                loanData.put("color", "#ED6A5B");
//                loanData.put("header", "Merchant Not Eligible");
//                loanData.put("message", reason);
//                data.put("loan_data", loanData);
//                data.put("task_enable", Boolean.FALSE);
//                responseDTO.setData(data);
//                return responseDTO;
//            }
//            EligibleLoan eligibleLoan = eligibleLoanDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
//            LendingApplication lendingApplication = lendingApplicationDao.findBymerchantId(merchantId);
//            if ((eligibleLoan == null && lendingApplication == null) || !isFosTaskEnabled) {
//                loanData.put("eligible", Boolean.FALSE);
//                loanData.put("color", "#ED6A5B");
//                loanData.put("header", "Merchant Not Eligible");
//                loanData.put("message", "Merchant Not Eligible For Loan.");
//                data.put("loan_data", loanData);
//                data.put("task_enable", Boolean.FALSE);
//                responseDTO.setData(data);
//                return responseDTO;
//            }
//            if (eligibleLoan != null && !LoanType.SMALL_TICKET.name().equals(eligibleLoan.getLoanType()) && lendingApplication == null) {
//                loanData.put("eligible", Boolean.TRUE);
//                loanData.put("applicationPending", Boolean.FALSE);
//                loanData.put("color", "#02A758");
//                loanData.put("header", "Merchant is Eligible For Loan.");
//                loanData.put("message", "Please Apply Loan On BharatPe Merchant App.");
//                data.put("loan_data", loanData);
//                data.put("task_enable", Boolean.TRUE);
//                responseDTO.setData(data);
//                return responseDTO;
//            }
//            if (lendingApplication != null && !LoanType.SMALL_TICKET.name().equals(lendingApplication.getLoanType())) {
//                MerchantBankDetail merchantBankDetail = merchantBankDetailDao.findTop1ByMerchantIdAndStatusOrderByIdDesc(merchantId, "ACTIVE");
//                logger.info("Merchant bank Detais", merchantBankDetail);
//                if ("draft".equals(lendingApplication.getStatus())) {
//                    loanData.put("applicationPending", Boolean.TRUE);
//                    loanData.put("eligible", Boolean.TRUE);
//                    loanData.put("loan_applied", Boolean.TRUE);
//                    loanData.put("color", "#EAA003");
//                    loanData.put("header", "Application is in Draft State.");
//                    loanData.put("message", "Please Complete Application.");
//                    data.put("loan_data", loanData);
//                    data.put("task_enable", Boolean.TRUE);
//                    responseDTO.setData(data);
//                    return responseDTO;
//                }
//                IfscSlave ifsc = ifscDaoSlave.findTop1ByIfscOrderByIdDesc(merchantBankDetail.getIfscCode());
//                BankListSlave bankList = bankListDaoSlave.findByBankCode(merchantBankDetail.getBankCode());
//                details.put("external_loan_id", lendingApplication.getExternalLoanId());
//                details.put("loan_amount", lendingApplication.getLoanAmount());
//                details.put("beneficiary_name", lendingApplication.getMerchantId());
//                details.put("account_type", merchantBankDetail.getAccType());
//                details.put("bank_name", merchantBankDetail.getBankName());
//                details.put("account_number", merchantBankDetail.getAccountNumber());
//                details.put("ifsc_code", merchantBankDetail.getIfscCode());
//                details.put("application_id", lendingApplication.getId());
//                details.put("branch", ifsc != null ? ifsc.getBranch() : " ");
//                details.put("bank_logo", bankList != null ? bankList.getImageUrl() : " ");
//                data.put("details", details);
//                if ("approved".equals(lendingApplication.getStatus())) {
//                    loanData.put("applicationPending", Boolean.FALSE);
//                    loanData.put("eligible", Boolean.TRUE);
//                    loanData.put("loan_applied", Boolean.TRUE);
//                    loanData.put("color", "#02A758");
//                    loanData.put("header", "Merchant Application is in approved state.");
//                    loanData.put("message", "It will take 7-10 days for disbursal process.");
//                    data.put("loan_data", loanData);
//                    data.put("task_enable", Boolean.FALSE);
//                    responseDTO.setData(data);
//                    return responseDTO;
//                }
//                if ("pending_verification".equals(lendingApplication.getStatus())) {
//                    loanData.put("applicationPending", Boolean.FALSE);
//                    loanData.put("limited_cpv_required", Boolean.FALSE);
//                    loanData.put("eligible", Boolean.TRUE);
//                    loanData.put("header", "Loan applied Succesfully");
//                    loanData.put("color", "#02A758");
//                    loanData.put("message", "Merchant Loan Application Is in Pending Verification State.");
//                    data.put("task_enable", Boolean.FALSE);
//                    loanData.put("agreement_at", lendingApplication.getAgreementAt().toString());
////                    if("REGULAR".equalsIgnoreCase(lendingApplication.getLoanType()) && lendingApplication.getLoanAmount()>50000){
////                        loanData.put("nachStatus","NotRequired");
////                        loanData.put("header","Loan applied Succesfully");
////                        loanData.put("loan_applied",Boolean.TRUE);
////                        loanData.put("message","Merchant Loan Application Is in Pending Verification State.");
////                        data.put("task_enable",Boolean.FALSE);
////                    }else{
//                    if (!"APPROVED".equals(lendingApplication.getNachStatus()) && !"NOT_STARTED".equalsIgnoreCase(lendingApplication.getNachStatus())) {
//                        BharatPeEnachSlave bharatPeEnachSkipped = bharatPeEnachDaoSlave.isSkipped(merchantId, lendingApplication.getId());
//                        Long bharatPeEnach = bharatPeEnachDaoSlave.isFailed(merchantId, lendingApplication.getId());
//                        BharatPeEnachSlave bharatPeEnachFailed = bharatPeEnachDaoSlave.findSpecificError(merchantId, lendingApplication.getId());
//                        if (bharatPeEnachSkipped == null && bharatPeEnach != null) {
//                            if (bharatPeEnach > 2 || bharatPeEnachFailed != null) {
//                                loanData.put("limited_cpv_required", Boolean.TRUE);
//                            }
//                        } else if (bharatPeEnachSkipped != null) {
//                            loanData.put("limited_cpv_required", Boolean.TRUE);
//                        }
//                        loanData.put("nachStatus", "Pending");
//                        loanData.put("color", "#EAA003");
//                        loanData.put("header", "Ask User To Complete eNACH");
//                        loanData.put("loan_applied", Boolean.FALSE);
//                        loanData.put("message", "Go To Loan Section On BharatPe Merchant App To  Start eNACH.");
//                        data.put("task_enable", Boolean.TRUE);
//                    } else {
//                        loanData.put("loan_applied", Boolean.TRUE);
//                        loanData.put("nachStatus", lendingApplication.getNachStatus().toLowerCase());
//                    }
////                    }
//                    data.put("loan_data", loanData);
//                    responseDTO.setData(data);
//                    return responseDTO;
//                } else {
//                    loanData.put("applicationPending", Boolean.FALSE);
//                    loanData.put("header", "Merchant Loan Application Is in Rejected State.");
//                    loanData.put("color", "#565652");
//                    loanData.put("message", "Loan has been Rejected, Please apply again through app");
//                    loanData.put("loan_applied", Boolean.FALSE);
//                    loanData.put("eligible", Boolean.TRUE);
//                    loanData.put("applicationRejected", Boolean.TRUE);
//                    data.put("loan_data", loanData);
//                    data.put("task_enable", Boolean.TRUE);
//                    responseDTO.setData(data);
//                    return responseDTO;
//                }
//            } else {
//                loanData.put("eligible", Boolean.FALSE);
//                loanData.put("color", "#ED6A5B");
//                loanData.put("header", "Merchant Not Eligible");
//                loanData.put("message", "Merchant Not Eligible For Loan.");
//                data.put("loan_data", loanData);
//                data.put("task_enable", Boolean.FALSE);
//                responseDTO.setData(data);
//                return responseDTO;
//            }
//        } catch (Exception ex) {
//            logger.error("Error Fos Loan Details API", ex);
//            return responseDTO;
//        }
//    }

    public ResponseDTO fosUpdate(Map<String, Object> requestDTO) {
        ResponseDTO responseDTO = new ResponseDTO();
        Long merchantId = Long.valueOf(requestDTO.containsKey("merchant_id") ? requestDTO.get("merchant_id").toString() : "0");
        Long applicationId = Long.valueOf(requestDTO.containsKey("application_id") ? requestDTO.get("application_id").toString() : "0");
        Long cpvAgentId = Long.valueOf(requestDTO.containsKey("agent_id") ? requestDTO.get("agent_id").toString() : "0");
        String accType = requestDTO.containsKey("account_type") ? requestDTO.get("account_type").toString() : null;
        if (merchantId == 0 || applicationId == 0 || cpvAgentId == 0 || accType == null) {
            responseDTO.setSuccess(Boolean.FALSE);
            responseDTO.setMessage("Require Parameter Missing In Request.");
            return responseDTO;
        }
        try {
            LendingApplication lendingApplication = lendingApplicationDao.findByIdAndMerchantId(applicationId, merchantId);
            if (lendingApplication == null) {
                responseDTO.setSuccess(Boolean.FALSE);
                responseDTO.setMessage("Application Id And Merchant Id Not Validated");
                return responseDTO;
            }
            if (!"APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus()) && !"approved".equalsIgnoreCase(lendingApplication.getStatus())) {
                lendingApplication.setNachReferenceNumber(lendingApplication.getExternalLoanId());
//                lendingApplication.setNachLender("BHARATPE");
                lendingApplication.setNachType("EXTERNAL");
                lendingApplication.setNachStatus("NOT_STARTED");
                lendingApplication.setPhysicalVerificationStatus("SUBMITTED");
                lendingApplication.setCpvAgentId(cpvAgentId);
                lendingApplication.setAssignedAt(new Date());
                lendingApplication.setCpvSubmitTimestamp(new Date());
                lendingApplicationDao.save(lendingApplication);

                apiGatewayService.fosAttribution(lendingApplication.getMerchantId(), "NTB_LOAN_V2", "CLOSED");
            }
            BharatPeEnachResponseDTO bharatPeEnach = enachHandler.findByMerchantIdAndApplicationId(merchantId, applicationId);
            if (bharatPeEnach != null && !bharatPeEnach.getSuccess()) {
                bharatPeEnach.setSkip(Boolean.TRUE);
//                LendingPennydrop lendingPennydrop = lendingPennydropDao.isFailed(lendingApplication.getMerchantId(), lendingApplication.getId());
//                if (lendingPennydrop == null) {
//                    apiGatewayService.updateApplicationPriority(lendingApplication.getMerchantId(), lendingApplication.getId());
//                }
                enachHandler.skipNach(applicationId, merchantId);
            }
            final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
            BankDetailsDto merchantBankDetail = bankDetailsDtoOptional.orElse(null);
            if (merchantBankDetail != null) {
                final boolean updated = merchantService.updateDetails(merchantId, Constants.MerchantUtil.Operation.SET, Constants.MerchantUtil.PartialUpdateKey.MerchantBankKeys.ACCOUNT_TYPE, accType);
                logger.info("Fos Update API merchant bank accounttype update : {}", updated);
                if (!updated){
                    responseDTO.setMessage("Something Went Wrong while updating accounttype!");
                    responseDTO.setSuccess(Boolean.FALSE);
                    return responseDTO;
                }
            }
//            LendingCpvDetails lendingCpvDetails = lendingCpvDetailsDao.findByMerchantIdAndApplicationIdAndAgentId(merchantId, applicationId, cpvAgentId);
//            if (lendingCpvDetails != null) {
//                lendingCpvDetails.setAccountType(accType);
//                lendingCpvDetailsDao.save(lendingCpvDetails);
//            }
            responseDTO.setMessage("Loan Application Updated Successfully!");
            responseDTO.setSuccess(Boolean.TRUE);
        } catch (Exception ex) {
            logger.info("Fos Update API Exception", ex);
            responseDTO.setMessage("Something Went Wrong!");
            responseDTO.setSuccess(Boolean.FALSE);
        }
        return responseDTO;
    }

//    public ResponseDTO getMerchantAddress(Long merchantId) {
//        ResponseDTO responseDTO = new ResponseDTO();
//
////        Merchant merchant = merchantDao.findById(merchantId).get();
//
//        try {
//            Map<String, Object> addressResponse = new HashMap<>();
//            LendingApplication lendingApplication = lendingApplicationDao.findApplicableApplication(merchantId);
//            if (Objects.nonNull(lendingApplication)) {
//                Object experianAddress = getExperianAddress(merchantId);
//                Object lendingAddress = getLendingAddress(lendingApplication);
//                Object documentIdProofAddress = getDocumentIdProofAddress(lendingApplication);
//                Object merchantInferredAddress = getMerchantInferredAddress(lendingApplication);
//
//                addressResponse.put("experian_address", experianAddress);
//                addressResponse.put("lending_address", lendingAddress);
//                addressResponse.put("document_id_proof_address", documentIdProofAddress);
//                addressResponse.put("merchant_inferred_address", merchantInferredAddress);
//
//                responseDTO.setMessage("Address");
//                responseDTO.setSuccess(Boolean.TRUE);
//                responseDTO.setData(addressResponse);
//
//                return responseDTO;
//            }
//        } catch (Exception ex) {
//            logger.error("Error while sending Address to fos App for Merchant: {} Error: {}", merchantId, ex);
//        }
//
//        responseDTO.setMessage("Something Went Wrong!");
//        responseDTO.setSuccess(Boolean.FALSE);
//        return responseDTO;
//    }


//    public Object getExperianAddress(Long merchant) {
////        Map<String, Object> experianAddress = new HashMap<>();
////        Experian experian = experianDao.getByMerchantId(merchant.getId());
////
////        JsonNode bureauResponse = parseStringResponse(experian.getResponse());;
////        if (bureauResponse == null) {
////            return new CreditScoreReportDetailDTO();
////        }
////        ResponseUtil responseUtil;
////        if ("CRIF".equalsIgnoreCase(experian.getBureau())) {
////            responseUtil = new CrifResponseUtil(experianDao);
////        } else {
////            responseUtil = new ExperianResponseUtil(experianDao);
////        }
//
//        return null;
//    }
//
//    public Object getLendingAddress(LendingApplication lendingApplication) {
//        try {
//            Map<String, Object> lendingAddress = new HashMap<>();
//
//            String address = Objects.isNull(lendingApplication.getShopNumber()) ? "" : lendingApplication.getShopNumber() + ",";
//            address += Objects.isNull(lendingApplication.getStreetAddress()) ? "" : lendingApplication.getStreetAddress() + ",";
//            address += Objects.isNull(lendingApplication.getArea()) ? "" : lendingApplication.getArea() + ",";
//            address += Objects.isNull(lendingApplication.getLandmark()) ? "" : lendingApplication.getLandmark() + ",";
//
//            address += Objects.isNull(lendingApplication.getCity()) ? "" : lendingApplication.getCity() + ",";
//            address += Objects.isNull(lendingApplication.getState()) ? "" : lendingApplication.getState() + ",";
//
//            lendingAddress.put("address", address);
//            lendingAddress.put("pincode", lendingApplication.getPincode());
//            lendingAddress.put("lat", lendingApplication.getLatitude());
//            lendingAddress.put("long", lendingApplication.getLongitude());
//
//            return lendingAddress;
//        } catch (Exception ex) {
//            logger.error("Error while sending LendingAddress to fos App for Application Id: {} Error: {}", lendingApplication.getId(), ex);
//        }
//
//        return null;
//    }
//
//
//    public Object getDocumentIdProofAddress(LendingApplication lendingApplication) {
//        try {
//
//            DocKycDetailsMaster docKycDetails = docKycDetailsDaoMaster.getAadharAddress(lendingApplication.getId());
//            logger.error("check for getDocumentIdProofAddress :{}", docKycDetails);
//            if (Objects.nonNull(docKycDetails)) {
//                Map<String, Object> docAddress = new HashMap<>();
//
//                docAddress.put("pincode", docKycDetails.getPincode());
//                docAddress.put("city", docKycDetails.getCity());
//                docAddress.put("state", docKycDetails.getState());
//                docAddress.put("address", docKycDetails.getAddress());
//                docAddress.put("lat", docKycDetails.getDocumentsIdProof().getLatitude());
//                docAddress.put("long", docKycDetails.getDocumentsIdProof().getLongitude());
//
//                return docAddress;
//            }
//        } catch (Exception ex) {
//            logger.error("Error while sending DocumentIdProof to fos App for Application Id: {} Error: {}", lendingApplication.getId(), ex);
//        }
//        return null;
//    }
//
//    public Object getMerchantInferredAddress(LendingApplication lendingApplication) {
//        try {
//            MerchantInferredLocationSlave merchantInferredLocation = merchantInferredLocationDaoSlave.findTop1ByMerchantIdOrderByIdDesc(lendingApplication.getMerchantId());
//
//            if (Objects.nonNull(merchantInferredLocation)) {
//                Map<String, Object> lendingAddress = new HashMap<>();
//
//                String address = Objects.isNull(merchantInferredLocation.getAddress()) ? "" : merchantInferredLocation.getAddress() + ", ";
//                address += Objects.isNull(merchantInferredLocation.getCity()) ? "" : merchantInferredLocation.getCity() + ", ";
//                address += Objects.isNull(merchantInferredLocation.getState()) ? "" : merchantInferredLocation.getState() + ", ";
//
//                lendingAddress.put("address", address);
//                lendingAddress.put("initial_lat", merchantInferredLocation.getIntialLatitude());
//                lendingAddress.put("initial_long", merchantInferredLocation.getIntialLongitude());
//                lendingAddress.put("inferred_lat", merchantInferredLocation.getInferredLatitude());
//                lendingAddress.put("inferred_long", merchantInferredLocation.getInferredLongitude());
////                lendingAddress.put("pincode", merchantInferredLocation.getPincode());
//
//                return lendingAddress;
//            }
//        } catch (Exception ex) {
//            logger.error("Error while sending DocumentIdProof to fos App for Application Id: {} Error: {}", lendingApplication.getId(), ex);
//        }
//        return null;
//    }

    public FosResponseDTO getFosSalaryAttribution(FosAttributionRequestDTO request) {
        logger.info("FOS salary attribution request:{}", request);
        FosResponseDTO responseDTO = new FosResponseDTO();
        try {
            List<LoanAttribution> loanAttributions = loanAttributionDao.getAttributionByMerchantIdAndRefCode(request.getMerchantId(), request.getFseRefcode());
            FosAttributionResponseDTO fosAttributionResponseDTO = new FosAttributionResponseDTO();
            if (Objects.isNull(loanAttributions) || loanAttributions.isEmpty()) {
                logger.info("loan attribution not found for merchant:{}", request.getMerchantId());
                fosAttributionResponseDTO.setStatus("MAYBE");

                responseDTO.setSuccess(true);
                responseDTO.setMessage("Attribution state");
                responseDTO.setData(fosAttributionResponseDTO);
                responseDTO.setStatusCode("200");
                return responseDTO;
            }
            for (LoanAttribution loanAttribution : loanAttributions) {
                logger.info("Checking loan attribution:{} for merchant:{}", loanAttribution, request.getMerchantId());
                LendingApplication lendingApplication = lendingApplicationDao.findById(loanAttribution.getApplicationId()).get();
                boolean enachDone = loanUtil.isEnachDone(lendingApplication.getMerchantId());
                if (enachDone && Objects.nonNull(loanAttribution.getEnachAttributedAt())) {
                    fosAttributionResponseDTO = isEnachAttributed(request, loanAttribution, lendingApplication);
                    if (fosAttributionResponseDTO.getStatus().equalsIgnoreCase("YES")) {
                        responseDTO.setSuccess(true);
                        responseDTO.setMessage("Attribution state");
                        responseDTO.setData(fosAttributionResponseDTO);
                        responseDTO.setStatusCode("200");
                        return responseDTO;
                    }
                }
                if ("REGULAR".equalsIgnoreCase(loanAttribution.getLoanType()) && loanAttribution.getLoanAmount() > 50000) {

                    fosAttributionResponseDTO = isAgreementAttributed(request, loanAttribution, lendingApplication);
                    if (fosAttributionResponseDTO.getStatus().equalsIgnoreCase("YES")) {
                        responseDTO.setSuccess(true);
                        responseDTO.setMessage("Attribution state");
                        responseDTO.setData(fosAttributionResponseDTO);
                        responseDTO.setStatusCode("200");
                        return responseDTO;
                    }
                } else if (("NTB".equalsIgnoreCase(loanAttribution.getLoanType()) || "OGL".equalsIgnoreCase(loanAttribution.getLoanType()) || ("REGULAR".equalsIgnoreCase(loanAttribution.getLoanType()) && loanAttribution.getLoanAmount() <= 50000))) {
                    if (Objects.nonNull(loanAttribution.getEnachAttributedAt()) && Objects.nonNull(loanAttribution.getAgreementAttributedAt())) {
                        if (loanAttribution.getAgreementAttributedAt().compareTo(loanAttribution.getEnachAttributedAt()) > 0) {
                            fosAttributionResponseDTO = isAgreementAttributed(request, loanAttribution, lendingApplication);
                            if (fosAttributionResponseDTO.getStatus().equalsIgnoreCase("YES")) {
                                responseDTO.setSuccess(true);
                                responseDTO.setMessage("Attribution state");
                                responseDTO.setData(fosAttributionResponseDTO);
                                responseDTO.setStatusCode("200");
                                return responseDTO;
                            }
                        } else {
                            fosAttributionResponseDTO = isEnachAttributed(request, loanAttribution, lendingApplication);
                            if (fosAttributionResponseDTO.getStatus().equalsIgnoreCase("YES")) {
                                responseDTO.setSuccess(true);
                                responseDTO.setMessage("Attribution state");
                                responseDTO.setData(fosAttributionResponseDTO);
                                responseDTO.setStatusCode("200");
                                return responseDTO;
                            }
                        }
                    } else if (Objects.nonNull(loanAttribution.getAgreementAttributedAt()) && enachDone) { //If enach was done on previous application
                        fosAttributionResponseDTO = isAgreementAttributed(request, loanAttribution, lendingApplication);
                        if (fosAttributionResponseDTO.getStatus().equalsIgnoreCase("YES")) {
                            responseDTO.setSuccess(true);
                            responseDTO.setMessage("Attribution state");
                            responseDTO.setData(fosAttributionResponseDTO);
                            responseDTO.setStatusCode("200");
                            return responseDTO;
                        }
                    } else {
                        fosAttributionResponseDTO.setStatus("NO");
                    }
                } else {
                    fosAttributionResponseDTO.setStatus("NO");
                }
            }

            responseDTO.setSuccess(true);
            responseDTO.setMessage("Attribution state");
            responseDTO.setData(fosAttributionResponseDTO);
            responseDTO.setStatusCode("200");
            return responseDTO;
        } catch (Exception ex) {
            logger.error("Exception while getting fos salary attribution for fos refNumber: {} and merchant: {}, ex", request.getFseRefcode(), request.getMerchantId(), ex);
        }
        responseDTO.setSuccess(false);
        responseDTO.setMessage("Something Went Wrong!");
        responseDTO.setStatusCode("500");
        return responseDTO;
    }

    private FosAttributionResponseDTO isAgreementAttributed(FosAttributionRequestDTO request, LoanAttribution loanAttribution, LendingApplication lendingApplication) {
        logger.info("Checking agreement attribution for merchant:{}", request.getMerchantId());
        FosAttributionResponseDTO fosAttributionResponseDTO = new FosAttributionResponseDTO();
        if (Objects.nonNull(loanAttribution.getAgreementAttributedAt())) {
            Long hourDiff = LoanUtil.getDateDiffInHour(request.getTaskStartedAt(), loanAttribution.getAgreementAttributedAt());
            logger.info("agreement time diff for merchant:{} is {}", request.getMerchantId(), hourDiff);
            if (hourDiff > -1 && hourDiff < 168 && "FOS".equalsIgnoreCase(loanAttribution.getAgreementAttributedTo())) {
                logger.info("merchant:{} is eligible for agreement attribution", request.getMerchantId());
                fosAttributionResponseDTO.setStatus("YES");
                if (Objects.nonNull(lendingApplication.getDisburseTimestamp())) {
                    fosAttributionResponseDTO.setStage("Amount Disbursed");
                    fosAttributionResponseDTO.setRemarks("Regular loan disbursed");
                } else {
                    fosAttributionResponseDTO.setStage("Application Completed");
                    fosAttributionResponseDTO.setRemarks("Regular loan application completed");
                }
            } else {
                fosAttributionResponseDTO.setStatus("NO");
                fosAttributionResponseDTO.setRemarks("Regular loan not eligible");
            }
            return fosAttributionResponseDTO;
        }

        fosAttributionResponseDTO.setStatus("NO");
        fosAttributionResponseDTO.setRemarks("Regular loan not eligible");
        return fosAttributionResponseDTO;
    }

    private FosAttributionResponseDTO isEnachAttributed(FosAttributionRequestDTO request, LoanAttribution loanAttribution, LendingApplication lendingApplication) {
        logger.info("Checking enach attribution for merchant:{}", request.getMerchantId());
        FosAttributionResponseDTO fosAttributionResponseDTO = new FosAttributionResponseDTO();
        if (Objects.nonNull(loanAttribution.getEnachAttributedAt())) {
            Long hourDiff = LoanUtil.getDateDiffInHour(request.getTaskStartedAt(), loanAttribution.getEnachAttributedAt());
            logger.info("Enach time diff for merchant:{} is {}", request.getMerchantId(), hourDiff);
            if (hourDiff > -1 && hourDiff < 168 && "FOS".equalsIgnoreCase(loanAttribution.getEnachAttributedTo())) {
                logger.info("merchant:{} is eligible for enach attribution", request.getMerchantId());
                fosAttributionResponseDTO.setStatus("YES");
                if (Objects.nonNull(lendingApplication.getDisburseTimestamp())) {
                    fosAttributionResponseDTO.setStage("Amount Disbursed");
                    fosAttributionResponseDTO.setRemarks(loanAttribution.getLoanType() + " loan disbursed");
                } else {
                    fosAttributionResponseDTO.setStage("Application Completed");
                    fosAttributionResponseDTO.setRemarks(loanAttribution.getLoanType() + " loan application completed");
                }
            } else {
                fosAttributionResponseDTO.setRemarks(loanAttribution.getLoanType() + " loan not eligible");
                fosAttributionResponseDTO.setStatus("NO");
            }
            return fosAttributionResponseDTO;
        }

        fosAttributionResponseDTO.setStatus("NO");
        fosAttributionResponseDTO.setRemarks(loanAttribution.getLoanType() + " loan not eligible");
        return fosAttributionResponseDTO;
    }
//    private JsonNode parseStringResponse(String response){
//        if (response == null || response.isEmpty()) return null;
//        try {
//            return objectMapper.readTree(response);
//        } catch (Exception e) {
//            logger.info("Exception while parsing string response ", e);
//            return null;
//        }
//    }

    public ResponseDTO checkMerchantEligibilty(Long merchantId, Boolean forceEligibilityCheck) {
        try {
//            Merchant merchant = merchantDao.getById(merchantId);
            Optional<BasicDetailsDto> merchant = merchantService.fetchMerchantBasicDetails(merchantId);
            // check for existing merchant
            if (!merchant.isPresent()) {
                logger.info("non existing merchant {}", merchantId);

                return computeEligibilityParams("ineligible", null, merchantId, "non existing merchant");
            }
            // is a store/d2r merchant
//            if ("ORGANIZED".equalsIgnoreCase(merchant.get().getCorrectMerchantType())
////                    || partnersApiHandler.isD2RMerchant(merchantId)
//            ) {
//                logger.info("is a store/d2r merchant {}", merchantId);
//                return computeEligibilityParams("ineligible", null, merchantId, "store merchant");
//            }
//            if (Objects.isNull(experian) || Objects.isNull(experian.getPancardNumber())) {
//                logger.info("merchant {} 's pan card doesn't exist", merchantId);
//                return computeEligibilityParams("maybe", null, merchantId, "experian/pan dne");
//            }
            // check for red pin
//            if (Objects.nonNull(experian.getPincode())) {
//                LendingPincodes lendingPincode = lendingPincodesDao.findByPincode(experian.getPincode());
//                if (Objects.isNull(lendingPincode) || (Objects.nonNull(lendingPincode) && lendingPincode.getColor().equals(PincodeColor.RED))) {
//                    logger.info("merchant {} is in red pin zone", merchantId);
//                    return computeEligibilityParams("ineligible", null, merchantId, "red pin zone");
//                }
//            }
            Experian experian = experianDao.getByMerchantId(merchantId);
            if (Objects.nonNull(experian)) {
                // blocked pan card
                if (Objects.nonNull(experian.getPancardNumber())) {
                    LendingBlockedPancard lendingBlockedPancard = lendingBlockedPancardDao.findTop1ByPancard(experian.getPancardNumber());
                    if (Objects.nonNull(lendingBlockedPancard)) {
                        logger.info("merchant {} 's pan card is blocked", merchantId);
                        return computeEligibilityParams("ineligible", null, merchantId, "blocked pan card");
                    }
                }
                // rejected experian
                if (experian.getRejected() && !"LIMIT BLOCKED: Pending application".equalsIgnoreCase(experian.getReason()) &&
                        LoanUtil.getDateDiffInDays(experian.getRejectedDate(), dateTimeUtil.getCurrentDate()) <
                                easyLoanUtil.getReapplyTime(experian.getReason(), RejectionStage.EXPERIAN, merchantId)) {
                    logger.info("merchant {} has a rejected entry in experian and has not elapsed the reapply timetime", merchantId);
                    return computeEligibilityParams("ineligible", null, merchantId, "non zero reapply timeline");
                }
            }
            // non nachable bank check
//            final Optional<BankDetailsDto> bankDetailsDtoOptional = merchantService.fetchMerchantBankDetails(merchantId);
//            BankDetailsDto merchantBankDetail = bankDetailsDtoOptional.orElse(null);
//            if (Objects.nonNull(merchantBankDetail)) {
//                LendingNachBankResponseDTO lendingNachBank = enachHandler.findByIfsc(merchantBankDetail.getIfsc().substring(0, 4));
//                if (Objects.isNull(lendingNachBank)) {
//                    logger.info("merchant {} has a non nachable bank", merchantId);
//                    return computeEligibilityParams("ineligible", null, merchantId, "non nachable bank");
//                }
//            }

            LendingApplication lendingApplication = lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchant.get().getId());
            // lending applications
            if (Objects.nonNull(lendingApplication)) {
                //draft
                Long reapplyTimeline = loanDetailsServiceV2.getReapplyTime(lendingApplication);
                if (lendingApplication.getStatus().equalsIgnoreCase("draft")) {
                    logger.info("merchant {} has a draft application", merchantId);
                    return computeEligibilityParams("eligible", "draft", merchantId, "draft application");
                }
                //rejected
                else if (Objects.nonNull(reapplyTimeline) && reapplyTimeline > 0) {
                    logger.info("merchant {} has a rejected application", merchantId);
                    return computeEligibilityParams("ineligible", null, merchantId, "rejected application");
                } else if (lendingApplication.getStatus().equalsIgnoreCase("pending_verification")) {
                    // pending nach
                    logger.info("merchant {} has a pending application", merchantId);
                    if (Objects.nonNull(lendingApplication.getAgreementAt()) && !"APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus())) {
                        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
                        if(Objects.nonNull(lendingApplicationDetails) && Objects.nonNull(lendingApplicationDetails.getCpvReferralCode())){
                            logger.info("Agreement for application:{} was done by FSE:{}", lendingApplication.getId(), lendingApplicationDetails.getCpvReferralCode());
                            if(Math.abs(dateTimeUtil.getDateDiffInDays(lendingApplication.getAgreementAt(), new Date())) > 7){
                                return computeEligibilityParams("eligible", "pending_nach", merchantId, "pending nach application");
                            }
                        } else{
                            logger.info("Agreement not done by FSE and nach pending for application:{}", lendingApplication.getId());
                            return computeEligibilityParams("eligible", "pending_nach", merchantId, "pending nach application");
                        }
                    }
                    // pending applications
                    else if (!ObjectUtils.isEmpty(lendingApplication.getNachStatus()) && lendingApplication.getNachStatus().equalsIgnoreCase("APPROVED")) {
                        logger.info("merchant {} has a pending application", merchantId);
                        return computeEligibilityParams("ineligible", null, merchantId, "pending application");
                    }
                } else if (lendingApplication.getStatus().equalsIgnoreCase("APPROVED")) {
                    // approved and not disbursed
                    if (Objects.isNull(lendingApplication.getDisburseTimestamp())) {
                        logger.info("merchant {} has an approved but not disbursed application", merchantId);
                        return computeEligibilityParams("ineligible", null, merchantId, "approved but not disbursed application");
                    } else {
                        LendingPaymentSchedule lendingPaymentSchedule = lendingPaymentScheduleDao.findTop1ByMerchantIdOrderByIdDesc(merchantId);
                        if (Objects.nonNull(lendingPaymentSchedule)) {
                            // active loans
                            if (lendingPaymentSchedule.getStatus().equalsIgnoreCase("ACTIVE")) {
                                logger.info("merchant {} has an active loan", merchantId);
                                return computeEligibilityParams("ineligible", null, merchantId, "active loan");
                            }
                            // closed loans and eligibility check
                            else if (lendingPaymentSchedule.getStatus().equalsIgnoreCase("CLOSED")) {
                                logger.info("merchant {} has a closed loan", merchantId);
                                return computeEligibilityParams(hasFinalOfferGtZero(merchant.get(), forceEligibilityCheck), null, merchantId, "closed loan");
                            }
                        }
                    }
                }
            }
            String finalOfferEligibility = hasFinalOfferGtZero(merchant.get(), forceEligibilityCheck);
            return finalOfferEligibility.equalsIgnoreCase("eligible") ? computeEligibilityParams("eligible", "not_started", merchantId, null) : computeEligibilityParams(finalOfferEligibility, null, merchantId, "no existing offer found");
        } catch (Exception e) {
            logger.error("error while checking fos loan eligibility for merchant: {}", merchantId, e);
        }
        return new ResponseDTO(Boolean.FALSE, "something went wrong !!");
    }

    public String hasFinalOfferGtZero(BasicDetailsDto merchant, Boolean forceEligibilityCheck) {
//        if (forceEligibilityCheck) {
//            try {
//                String globalDetailsCacheKey = "LENDING_GLOBAL_DETAILS_" + merchant.getId();
//                if (Objects.nonNull(lendingCache.get(globalDetailsCacheKey))) {
//                    logger.info("clearing cache for fos eligibility computation {}", merchant.getId());
//                    lendingCache.delete(globalDetailsCacheKey);
//                }
//                apiGatewayService.getGlobalLimit(merchant.getId());
//            } catch (Exception e) {
//                logger.error("error while computing final offer for merchant: {}", merchant.getId(), e);
//            }
//        }
        LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchant.getId());
        if (Objects.isNull(lendingRiskVariables)) {
            return "maybe";
        } else if (!ObjectUtils.isEmpty(lendingRiskVariables.getFinalOffer()) && lendingRiskVariables.getFinalOffer() > 0) {
            return "eligible";
        }
        return "ineligible";
    }

    public String getLoanType(String eligibility, Long merchantId) {
        switch (eligibility) {
            case "eligible":
                LendingRiskVariables lendingRiskVariables = lendingRiskVariablesDao.findByMerchantId(merchantId);
                return lendingRiskVariables != null ? lendingRiskVariables.getLoanType() : null;
            case "maybe":
                MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchantId);
                if (ObjectUtils.isEmpty(merchantResponseDTO)) {
                    throw new MerchantSummaryExceptionHandler(merchantId.toString());
                }
                return (!ObjectUtils.isEmpty(merchantResponseDTO.getUniqueCustomer1mon()) && merchantResponseDTO.getUniqueCustomer1mon() > 15 &&
                        !ObjectUtils.isEmpty(merchantResponseDTO.getFirstTransactionDate()) &&
                        dateTimeUtil.getDateDiffInDays(dateTimeUtil.getCurrentDate(), merchantResponseDTO.getFirstTransactionDate()) > 60 &&
                        merchantResponseDTO.getBpScore() >= 9) ? "REGULAR" : "NTB";
            default:
                return null;
        }
    }

    public String getOfferType(String eligibility) {
        switch (eligibility) {
            case "eligible":
                return "fixed";
            case "maybe":
                return "tentative";
            default:
                return null;
        }
    }

    public int getApplicationStatusWeight(String applicationStatus) {
        switch (String.valueOf(applicationStatus)) {
            case "pending_nach":
                return 3;
            case "draft":
                return 2;
            case "not_started":
                return 1;
            default:
                return 0;
        }
    }

    public int getEligibilityWeight(String eligibility) {
        switch (eligibility) {
            case "eligible":
                return 2;
            case "maybe":
                return 1;
            default:
                return 0;
        }
    }

    public int getLoanTypeWeight(String loanType) {
        switch (String.valueOf(loanType)) {
            case "REGULAR":
                return 2;
            case "NTB":
                return 1;
            default:
                return 0;
        }
    }

    public ResponseDTO computeEligibilityParams(String eligibility, String applicationStatus, Long merchantId, String reason) {
        String loanType = getLoanType(eligibility, merchantId);
        String offerType = getOfferType(eligibility);
        Integer priority = getEligibilityWeight(eligibility) * 100 + getLoanTypeWeight(loanType) * 10 + getApplicationStatusWeight(applicationStatus);
        FosMerchantEligibilityDto fosMerchantEligibilityDto = new FosMerchantEligibilityDto("SMALL_TICKET".equals(loanType)?"ineligible":eligibility, merchantId, priority, offerType, loanType, reason);
        ResponseDTO responseDTO = new ResponseDTO();
        responseDTO.setData(fosMerchantEligibilityDto);
        responseDTO.setSuccess(Boolean.TRUE);
        return responseDTO;
    }

    public ResponseDTO getFosTaskStatus(Long merchantId, Long taskTimestampEpoch, Long refCode, Integer timeWindow) {
        ResponseDTO responseDTO = new ResponseDTO();
        FosTaskStatusDto fosTaskStatusDto = new FosTaskStatusDto();
        try {
            Date taskStartTimestamp = dateTimeUtil.getDatePlusMinutes(new Date(taskTimestampEpoch * 1000), -1 * timeWindow);
            logger.info("task status timestamp: {}", taskStartTimestamp);
            Date taskEndTimeStamp = dateTimeUtil.getEndTimeFromDateTime(taskStartTimestamp);
//            Merchant merchant = merchantDao.getById(merchantId);
            LendingApplication lendingApplication = lendingApplicationDao.findBymerchantId(merchantId);
            if(ObjectUtils.isEmpty(lendingApplication)){
                logger.info("application not found");
                fosTaskStatusDto.setMessage("application not found");
                responseDTO.setSuccess(Boolean.TRUE);
                responseDTO.setData(fosTaskStatusDto);
                return responseDTO;
            }
            Optional<BasicDetailsDto> basicDetailsDto = merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId());
            if (ObjectUtils.isEmpty(basicDetailsDto)) {
                logger.info("invalid merchant ID");
                fosTaskStatusDto.setMessage("merchant doesn't exist");
                responseDTO.setData(fosTaskStatusDto);
                responseDTO.setSuccess(Boolean.TRUE);
                return responseDTO;
            }
            MerchantNachDetailsResponseDTO bpEnach = enachHandler.findSuccessEnach(merchantId, lendingApplication.getId());
            if (ObjectUtils.isEmpty(lendingApplication) ||
                    ((lendingApplication.getCreatedAt().before(taskStartTimestamp) || lendingApplication.getCreatedAt().after(taskEndTimeStamp)) &&
                            (ObjectUtils.isEmpty(lendingApplication.getAgreementAt()) || (lendingApplication.getAgreementAt().before(taskStartTimestamp) ||
                                    lendingApplication.getAgreementAt().after(taskEndTimeStamp))
                                    && (ObjectUtils.isEmpty(bpEnach) || bpEnach.getUpdatedAt().before(taskStartTimestamp) || bpEnach.getUpdatedAt().after(taskEndTimeStamp))
                            ))) {
                fosTaskStatusDto.setStatus("INCOMPLETE");
                fosTaskStatusDto.setStage(ApplicationStage.NOT_STARTED.getStage());
                fosTaskStatusDto.setMessage("no application found against this task");
                responseDTO.setData(fosTaskStatusDto);
                responseDTO.setSuccess(Boolean.TRUE);
                logger.info("no application found against this task for merchant {} {}", lendingApplication.getMerchantId(), fosTaskStatusDto);
                return responseDTO;
            } else {
                if (!ObjectUtils.isEmpty(lendingApplication.getNachStatus()) && lendingApplication.getNachStatus().equals("APPROVED")) {
                    fosTaskStatusDto.setStatus("COMPLETE");
                    fosTaskStatusDto.setMessage("task completed");
                    logger.info("nach done for merchant {}", merchantId);
                } else {
                    fosTaskStatusDto.setStatus("INCOMPLETE");
                    fosTaskStatusDto.setMessage("Nach pending for the application");
                    logger.info("nach pending for merchant {}", merchantId);
                }
                fosTaskStatusDto.setStatus("COMPLETE");
                fosTaskStatusDto.setMessage("task completed");
                logger.info("agreement done for merchant {}", lendingApplication.getMerchantId());
                LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
                if(ObjectUtils.isEmpty(lendingApplicationDetails)){
                    lendingApplicationDetails = new LendingApplicationDetails();
                    lendingApplicationDetails.setApplicationId(lendingApplication.getId());
                }
                lendingApplicationDetails.setCpvReferralCode(String.valueOf(refCode));
                logger.info("updated lending_application_details -> {}", lendingApplicationDetails.getCpvReferralCode());
                lendingApplicationDetailsDao.save(lendingApplicationDetails);
                saveRefCodeAudit(lendingApplicationDetails, String.valueOf(refCode), lendingApplication, "LOAN_TASK");
                populateApplicationStage(lendingApplication, fosTaskStatusDto);
                if (lendingApplication.getLoanType().equalsIgnoreCase("SMALL_TICKET")) {
                    fosTaskStatusDto.setEligibleForPayout("NO");
                    fosTaskStatusDto.setLoanType(lendingApplication.getLoanType());
                } else {
                    LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplication.getId());
                    fosTaskStatusDto.setEligibleForPayout("YES");
                    fosTaskStatusDto.setLoanType(ObjectUtils.isEmpty(lendingRiskVariablesSnapshot)? null:lendingRiskVariablesSnapshot.getRiskSegment().name());
                }
                fosTaskStatusDto.setApplicationId(lendingApplication.getId());
                responseDTO.setData(fosTaskStatusDto);
                responseDTO.setSuccess(Boolean.TRUE);
                logger.info("fos task status for merchant, {} {}", lendingApplication.getMerchantId(), fosTaskStatusDto);
                return responseDTO;
            }
        } catch (Exception e) {
            logger.error("exception occurred while fetching fos task status for merchant: {}", merchantId, e);
        }
        return new ResponseDTO(Boolean.FALSE, "something went wrong!!");
    }

    private void populateApplicationStage(LendingApplication lendingApplication, FosTaskStatusDto fosTaskStatusDto) {
        try {
            if ("draft".equalsIgnoreCase(lendingApplication.getStatus())) {
                fosTaskStatusDto.setStage(ApplicationStage.DRAFT.getStage());
            } else if ("pending_verification".equalsIgnoreCase(lendingApplication.getStatus())) {
                if (!ObjectUtils.isEmpty(lendingApplication.getNachStatus()) && "APPROVED".equalsIgnoreCase(lendingApplication.getNachStatus())) {
                    fosTaskStatusDto.setStage(ApplicationStage.RELEVANT.getStage());
                } else {
                    fosTaskStatusDto.setStage(ApplicationStage.SUBMITTED.getStage());
                }
            } else if ("rejected".equalsIgnoreCase(lendingApplication.getStatus())) {
                fosTaskStatusDto.setStage(ApplicationStage.REJECTED.getStage());
            } else if ("deleted".equalsIgnoreCase(lendingApplication.getStatus())) {
                fosTaskStatusDto.setStage(ApplicationStage.NOT_STARTED.getStage());
            } else if ("approved".equalsIgnoreCase(lendingApplication.getStatus())) {
                fosTaskStatusDto.setStage(ApplicationStage.RELEVANT.getStage());
            }
        } catch (Exception ex) {
            logger.error("Exception Occurred while populating Application stage for merchant: {}, {}", lendingApplication.getMerchantId(), ex);
        }
    }

    public ResponseDTO fosLoanTaskAttribution(Map<String, Object> request){
        if(ObjectUtils.isEmpty(request) || !request.containsKey("application_id") || ObjectUtils.isEmpty(request.get("application_id"))){
            return new ResponseDTO(Boolean.FALSE, "Required Parameters missing", null);
        }
        Long applicationId = Long.valueOf(request.get("application_id").toString());

        logger.info("FOS Attribution API called for applicationId:{}", applicationId);
        String nachFlag,disbursalFlag;

        try{
            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
            if(!lendingApplication.isPresent()){
                logger.error("Application with id:{} not found", applicationId);
                return new ResponseDTO(Boolean.FALSE, "application not found", null);
            }
            LendingApplication finalLendingApplication = lendingApplication.get();
            if(!ObjectUtils.isEmpty(finalLendingApplication.getDisburseTimestamp()) && "DISBURSED".equals(finalLendingApplication.getLoanDisbursalStatus())){
                disbursalFlag = "YES";
            }
            else if (!"deleted".equalsIgnoreCase(finalLendingApplication.getStatus()) && !"rejected".equalsIgnoreCase(finalLendingApplication.getStatus())){
                disbursalFlag = "MAYBE";
            }
            else disbursalFlag = "NO";
            MerchantNachDetailsResponseDTO enach = enachHandler.findSuccessEnach(finalLendingApplication.getMerchantId(), applicationId);
            logger.info("findSuccessEnachResponse:{}", enach);
            if(ObjectUtils.isEmpty(enach) && Math.abs(dateTimeUtil.getDateDiffInDays(new Date(), finalLendingApplication.getAgreementAt())) <= 7 &&
                    !"APPROVED".equalsIgnoreCase(finalLendingApplication.getNachStatus())){
                nachFlag = "MAYBE";
            }
            else if(!ObjectUtils.isEmpty(enach) && Math.abs(dateTimeUtil.getDateDiffInDays(finalLendingApplication.getAgreementAt(), enach.getUpdatedAt())) <= 7 &&
                    "APPROVED".equalsIgnoreCase(finalLendingApplication.getNachStatus())){
                nachFlag = "YES";
            }
            else nachFlag = "NO";
            return createCompoundStatusForAttribution(nachFlag, disbursalFlag);
        } catch(Exception ex){
            logger.error("Error occurred while checking task status for applicationId:{}, {}, {}", applicationId, ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            return new ResponseDTO(Boolean.FALSE, "Error occurred while checking task status", null);
        }
    }

    public ResponseDTO createCompoundStatusForAttribution(String nachFlag, String disbursalFlag) {
        logger.info("Creating compound status -> NACH:{} | DISBURSAL:{}", nachFlag, disbursalFlag);
        Map<String, Object> response = new HashMap<>();
        if("NO".equals(nachFlag)) {
            response.put("status", nachFlag);
            response.put("stage_name", "NACH");
        }
        else if ("MAYBE".equals(nachFlag)){
            response.put("status", nachFlag);
            response.put("stage_name", "NACH");
        }
        else{
            if("YES".equals(disbursalFlag)){
                response.put("status", disbursalFlag);
                response.put("stage_name", "DISBURSAL");
            }
            else if ("NO".equals(disbursalFlag)){
                response.put("status", disbursalFlag);
                response.put("stage_name", "DISBURSAL");
            }
            else{
                response.put("status", nachFlag);
                response.put("stage_name", "NACH");
            }
        }
        logger.info("create compound status response: {}", response);
        return new ResponseDTO(Boolean.TRUE, null, response);
    }

    public ResponseDTO nachTaskSuccess(Map<String, Object> request){

        Long merchantId = Long.valueOf(request.containsKey("merchant_id") ? request.get("merchant_id").toString() : "0");
        Long visitTimestamp = Long.valueOf(request.containsKey("visit_timestamp") ? request.get("visit_timestamp").toString() : "0");
        int timeWindow = Integer.parseInt(request.containsKey("time_window") ? request.get("time_window").toString() : "0");
        String refCode = String.valueOf(request.containsKey("ref_code") ? request.get("ref_code").toString() : null);
        if (merchantId == 0 || visitTimestamp == 0 || timeWindow == 0 || refCode == null) {
            return new ResponseDTO(Boolean.FALSE, "Required parameteers Missing", null);
        }

        Map<String, Object> response = new HashMap<>();
        LendingApplication lendingApplication = lendingApplicationDao.findBymerchantId(merchantId);
        logger.info("lendingApplication -> {}", lendingApplication);
        if(ObjectUtils.isEmpty(lendingApplication)){
            logger.info("Application not found for merchant: {}", merchantId);
            response.put("message", "Application not found");
            response.put("status", false);
            response.put("application_id", null);
            return new ResponseDTO(Boolean.FALSE, "Application not found.", response);
        }
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(lendingApplication.getId());
        response.put("ref_code", ObjectUtils.isEmpty(lendingApplicationDetails) ? null:lendingApplicationDetails.getCpvReferralCode());
        response.put("agreement_at", ObjectUtils.isEmpty(lendingApplication) ? null:lendingApplication.getAgreementAt());
        try{
            Date taskStartTimestamp = dateTimeUtil.getDatePlusMinutes(new Date(visitTimestamp * 1000), -1 * timeWindow);
            logger.info("task status timestamp: {}", taskStartTimestamp);
            Date taskEndTimeStamp = dateTimeUtil.getEndTimeFromDateTime(taskStartTimestamp);
            MerchantNachDetailsResponseDTO enach = enachHandler.findSuccessEnach(lendingApplication.getMerchantId(), lendingApplication.getId());
            logger.info("findSuccessEnachResponse:{}", enach);
            if(!ObjectUtils.isEmpty(enach) && enach.getUpdatedAt().after(taskStartTimestamp) && enach.getUpdatedAt().before(taskEndTimeStamp)){
                if(!ObjectUtils.isEmpty(lendingApplication.getNachStatus()) && "APPROVED".equals(lendingApplication.getNachStatus())){
                    if(ObjectUtils.isEmpty(lendingApplicationDetails)){
                        lendingApplicationDetails = new LendingApplicationDetails();
                        lendingApplicationDetails.setApplicationId(lendingApplication.getId());
                    }
                    logger.info("nach done for application:{}", lendingApplication.getId());
                    logger.info("lending_application_details refCode for nach -> {}", lendingApplicationDetails.getCpvReferralCodeNach());
                    lendingApplicationDetails.setCpvReferralCodeNach(refCode);
                    lendingApplicationDetailsDao.save(lendingApplicationDetails);
                    saveRefCodeAudit(lendingApplicationDetails, refCode, lendingApplication, "NACH_TASK");
                    response.put("message", "nach done for the application");
                    response.put("status", true);
                    response.put("application_id", lendingApplication.getId());
                    return new ResponseDTO(Boolean.TRUE, "nach done for the application", response);
                } else{
                    logger.info("nach is pending for application:{}", lendingApplication.getId());
                    response.put("message", "nach is pending for the application");
                    response.put("status", false);
                    response.put("application_id", null);
                    return new ResponseDTO(Boolean.FALSE, "nach is pending for the application", response);
                }
            } else{
                logger.info("no application found against this task");
                response.put("message", "no application found against this task");
                response.put("status", false);
                response.put("application_id", null);
                return new ResponseDTO(Boolean.FALSE, "no application found against this task", response);
            }
        } catch (Exception ex){
            logger.error("Error occurred while checking nach status for merchant:{}, {}, {}", merchantId, ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            response.put("message", "Error occurred while checking nach status");
            response.put("status", false);
            response.put("application_id", null);
            return new ResponseDTO(Boolean.FALSE, "Error occurred while checking nach status", response);
        }
    }

    private void saveRefCodeAudit(LendingApplicationDetails lendingApplicationDetails, String refCode, LendingApplication lendingApplication, String auditType){
        logger.info("Auditing lending Application Details changes for :{}", auditType);
        LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
        lendingAuditTrial.setApplicationId(lendingApplicationDetails.getApplicationId());
        lendingAuditTrial.setLoanId(ObjectUtils.isEmpty(lendingApplication.getExternalLoanId())?"":lendingApplication.getExternalLoanId());
        lendingAuditTrial.setMerchantId(lendingApplication.getMerchantId());
        lendingAuditTrial.setType("NACH_TASK".equals(auditType) ?"REF_CODE_NACH_UPDATE":"REF_CODE_UPDATE");
        lendingAuditTrial.setOldStatus("NACH_TASK".equals(auditType) ? lendingApplicationDetails.getCpvReferralCodeNach() : lendingApplicationDetails.getCpvReferralCode());
        lendingAuditTrial.setNewStatus(refCode);
        logger.info("lendingAuditTrial -> {}", lendingAuditTrial);
        lendingAuditTrialDao.save(lendingAuditTrial);
    }

    public ResponseDTO nachAttribution(Long refCode, Long applicationId){
        logger.info("disbursal attribution for application:{}", applicationId);
        Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
        LendingApplicationDetails lendingApplicationDetails = lendingApplicationDetailsDao.findLendingApplicationDetailsByApplicationId(applicationId);
        if(!lendingApplication.isPresent()){
            logger.info("application not found with id:{}", applicationId);
            return new ResponseDTO(Boolean.FALSE, "application not found.", null);
        }
        Map<String, Object> response = new HashMap<>();
        String disbursalStatus="";
        MerchantNachDetailsResponseDTO enach = enachHandler.findSuccessEnach(lendingApplication.get().getMerchantId(), lendingApplication.get().getId());
        if(!ObjectUtils.isEmpty(enach) && !ObjectUtils.isEmpty(enach.getNachLender()) && enach.getNachLender().equals(loanUtil.enachServiceLenderMapper(lendingApplication.get().getLender()))
                && String.valueOf(refCode).equals(lendingApplicationDetails.getCpvReferralCodeNach())){
            logger.info("enach successfully done for application:{}", applicationId);
            if(!ObjectUtils.isEmpty(lendingApplication.get().getDisburseTimestamp()) && "DISBURSED".equals(lendingApplication.get().getLoanDisbursalStatus())){
                logger.info("disbursal done for application:{}", applicationId);
                disbursalStatus = "YES";
            } else if (!"rejected".equalsIgnoreCase(lendingApplication.get().getStatus()) && !"deleted".equalsIgnoreCase(lendingApplication.get().getStatus())){
                logger.info("disbursal not done yet for application:{}", applicationId);
                disbursalStatus = "MAYBE";
            } else disbursalStatus = "NO";
        } else disbursalStatus = "NO";
        response.put("disbursal_status", disbursalStatus);
        return new ResponseDTO(Boolean.TRUE, null, response);
    }
}
