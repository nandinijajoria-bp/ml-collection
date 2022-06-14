package com.bharatpe.lending.service;

import com.bharatpe.common.dao.AvailableLoanDao;
import com.bharatpe.common.dao.EligibleLoanDao;
import com.bharatpe.common.entities.*;
import com.bharatpe.lending.common.Handler.MerchantSummaryHandler;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.dto.MerchantResponseDTO;
import com.bharatpe.lending.common.entity.MerchantDocumentProof;
import com.bharatpe.lending.common.entity.MerchantDocumentProofOcr;
import com.bharatpe.lending.common.entity.MerchantDocumentProofRequest;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.LendingAuditTrialDao;
import com.bharatpe.lending.dao.LendingCategoryDao;
import com.bharatpe.lending.dao.LendingPaymentScheduleDao;
import com.bharatpe.lending.dao.TmpLoanGenerateDao;
import com.bharatpe.lending.dto.MetaDTO;
import com.bharatpe.lending.dto.RequestDTO;
import com.bharatpe.lending.dto.SignAgreementDTO;
import com.bharatpe.lending.handlers.BharatPeOtpHandler;
import com.bharatpe.lending.util.LoanCalculationUtil;
import com.bharatpe.lending.util.LoanCalculationUtil.LoanBreakupDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CreditSignAgreementService {

    Logger logger = LoggerFactory.getLogger(SignAgreementService.class);

    @Autowired
    CreditApplicationDao creditApplicationDao;

    @Autowired
    CreditApplicationService creditApplicationService;
    @Autowired
    CreditApplicationAddressDao creditApplicationAddressDao;
    @Autowired
    MerchantDocumentProofDao merchantDocumentProofDao;

//	@Autowired
//	MerchantSummaryDao merchantSummaryDao;

    @Autowired
    LendingPaymentScheduleDao lendingPaymentScheduleDao;

    @Autowired
    AvailableLoanDao availableLoanDao;

    @Autowired
    LendingCategoryDao lendingCategoryDao;

    @Autowired
    LendingAuditTrialDao lendingAuditTrialDao;

    @Autowired
    TmpLoanGenerateDao tmpLoanGenerateDao;

    @Autowired
    MerchantDocumentProofRequestDao merchantDocumentProofRequestDao;

    @Autowired
    MerchantDocumentProofOcrDao merchantDocumentProofOcrDao;

    @Autowired
    BharatPeOtpHandler bharatPeOtpHandler;

    @Autowired
    LendingApplicationService lendingApplicationService;

    @Autowired
    EligibleLoanDao eligibleLoanDao;

    @Value("${experian.enable:true}")
    Boolean EXPERIAN_ENABLED;

    @Autowired
    APIGatewayService apiGatewayService;

    @Autowired
    MerchantSummaryHandler merchantSummaryHandler;

    public Map<String, Object> signAgreement(BasicDetailsDto merchant, RequestDTO<SignAgreementDTO> requestDTO) {
        Map<String, Object> finalResponse = new LinkedHashMap<>();
        finalResponse.put("success", false);
        finalResponse.put("otp_flow", false);

        Boolean agreement = requestDTO.getPayload().getAgreement();
        if (agreement == null || !agreement) {
            return finalResponse;
        }

        Long applicationId = requestDTO.getPayload().getApplicationId();

        if (applicationId != null && applicationId != 0) {
            finalResponse = verifyApplicationAndSendOTP(merchant, applicationId);
        } else {
            finalResponse = createNewApplicationAndSendOTP(requestDTO, merchant);
        }

        return finalResponse;
    }

    private Map<String, Object> verifyApplicationAndSendOTP(BasicDetailsDto merchant, Long applicationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("otp_flow", false);

        CreditApplication creditApplication = creditApplicationDao.findByIdAndMerchantId(applicationId, merchant.getId());

        if (creditApplication == null || !"draft".equals(creditApplication.getStatus())) {
            logger.info("Application is empty or status is not in draft with id {}, returing.", applicationId);
            return response;
        }

        List<MerchantDocumentProof> documentsIdProofList = merchantDocumentProofDao.findByMerchantIdAndOwnerIdAndOwnerType(merchant.getId(), creditApplication.getId(), "LENDING");
        if (documentsIdProofList == null || documentsIdProofList.size() == 0) {
            return response;
        }
        response = sendOTP(merchant.getMobile());
        response.put("application_id", applicationId);
        return response;
    }

    private Map<String, Object> createNewApplicationAndSendOTP(RequestDTO<SignAgreementDTO> requestDTO, BasicDetailsDto merchant) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("otp_flow", false);

        String selectedCategory = requestDTO.getPayload().getCategory();

        if (StringUtils.isEmpty(selectedCategory)) {
            logger.error("Selected category is null/empty for merchant {}", merchant.getId());
            return response;
        }

//		MerchantSummary merchantSummary = merchantSummaryDao.findByMerchantId(merchant.getId());
        MerchantResponseDTO merchantResponseDTO = merchantSummaryHandler.getMerchantSummary(merchant.getId());
        if (merchantResponseDTO == null) {
            logger.error("Merchant summary is empty for merchant with id {}", merchant.getId());
            return response;
        }

        LendingPaymentSchedule prevLendingSchedule = lendingPaymentScheduleDao.findLatestCreditLoanByMerchantId(merchant.getId());
        CreditApplication prevApplication = creditApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        CreditApplicationAddress prevApplicationAddress = creditApplicationAddressDao.findByMerchantIdAndApplicationId(merchant.getId(), prevApplication.getId());

        if (prevLendingSchedule == null || prevApplication == null) {
            logger.error("User not eligible, last loan not found or last application is not disbursed/found");
            return response;
        }

        // pin code check for loan eligibility
        try {
            logger.info("Starting pin code check for loan eligibilty ");
            response.put("code", LendingConstants.LOAN_APPLICATION_SUCCESS_CODE);
            response.put("message", LendingConstants.LOAN_APPLICATION_SUCCESS_MESSAGE);
            if (!creditApplicationService.checkLoanRequestPinCodeForLoanEligibilty(Integer.valueOf(prevApplication.getPincode()))) {
                logger.error("Pincode {} not eligible for the loan", Integer.valueOf(prevApplication.getPincode()));
                response.put("code", LendingConstants.LOAN_APPLICATION_OGL_CODE);
                response.put("message", LendingConstants.LOAN_APPLICATION_OGL_MESSAGE);
                return response;
            }
        } catch (Exception e) {
            logger.error("Error ocuured while checking loan eligibilty for pin code {}", Integer.valueOf(prevApplication.getPincode()));
        }
        LendingCategories selectedCategoriesData = lendingCategoryDao.getByCategory(selectedCategory);
        CreditApplication newApplication = new CreditApplication();
        CreditApplicationAddress newApplicationAddress = new CreditApplicationAddress();
        if (EXPERIAN_ENABLED) {
            List<EligibleLoan> eligibleLoans = eligibleLoanDao.findByMerchantIdAndCategory(merchant.getId(), selectedCategory);
            if (eligibleLoans == null || eligibleLoans.isEmpty()) {
                logger.error("No availabel loan found with merchant id {} and loan category {}", merchant.getId(), selectedCategory);
                return response;
            }
            EligibleLoan eligibleLoan = eligibleLoans.get(0);

            if (!"TOPUP".equalsIgnoreCase(eligibleLoan.getLoanType()) && (!prevLendingSchedule.getStatus().equals("CLOSED") || (!"deleted".equalsIgnoreCase(prevApplication.getStatus()) && !"DISBURSED".equalsIgnoreCase(prevApplication.getStatus())))) {
                logger.info("Last loan not closed for merchant ID {}", merchant.getId());
                return response;
            }
            int processingFee;
            if (apiGatewayService.eligibleForProcessingFee(merchant.getId())) {
                processingFee = 0;
            } else {
                processingFee = (int) Math.ceil(eligibleLoan.getAmount() * Double.parseDouble(selectedCategoriesData.getProcessingFee()));
            }
            if (!"TOPUP".equalsIgnoreCase(eligibleLoan.getLoanType()))
                newApplication.setDisbursalAmount(eligibleLoan.getAmount() - processingFee);
            newApplication.setMerchantId(merchant.getId());
            newApplication.setMerchantStoreId(prevApplication.getMerchantStoreId());
            newApplicationAddress.setStreetAddress(prevApplicationAddress.getStreetAddress());
            newApplicationAddress.setArea(prevApplicationAddress.getArea());
            newApplicationAddress.setLandmark(prevApplicationAddress.getLandmark());
            newApplicationAddress.setPincode(prevApplicationAddress.getPincode());
            newApplicationAddress.setCity(prevApplicationAddress.getCity());
            newApplicationAddress.setState(prevApplicationAddress.getState());
            newApplication.setBusinessName(prevApplication.getBusinessName());
            newApplication.setStatus("draft");
            newApplication.setCategory(selectedCategory);
            newApplication.setAmount(eligibleLoan.getAmount());
            newApplication.setLoanType(eligibleLoan.getLoanType());
        } else {
            List<AvailableLoan> availableLoanList = availableLoanDao.findByMerchantIdAndTypeOrderByAmountDesc(merchant.getId(), merchantResponseDTO.getLoanType());
            AvailableLoan selectedAvailableLoan = null;

            if (!prevLendingSchedule.getStatus().equals("CLOSED") || (!"deleted".equalsIgnoreCase(prevApplication.getStatus()) && !"DISBURSED".equalsIgnoreCase(prevApplication.getStatus()))) {
                logger.info("Last loan not closed for merchant ID {}", merchant.getId());
                return response;
            }

            for (AvailableLoan current : availableLoanList) {
                if (current.getCategory().equals(selectedCategory)) {
                    selectedAvailableLoan = current;
                    break;
                }
            }
            if (selectedAvailableLoan == null) {
                logger.error("No availabel loan found with merchant id {} and loan category {}", merchant.getId(), selectedCategory);
                return response;
            }
            LoanBreakupDetail breakup = LoanCalculationUtil.getLoanBreakup(selectedAvailableLoan, selectedCategoriesData, null);

            newApplication.setDisbursalAmount(Double.valueOf(breakup.getDisbursementAmount()));
            newApplication.setMerchantId(merchant.getId());
            newApplication.setMerchantStoreId(prevApplication.getMerchantStoreId());
            newApplicationAddress.setStreetAddress(prevApplicationAddress.getStreetAddress());
            newApplicationAddress.setArea(prevApplicationAddress.getArea());
            newApplicationAddress.setLandmark(prevApplicationAddress.getLandmark());
            newApplicationAddress.setPincode(prevApplicationAddress.getPincode());
            newApplicationAddress.setCity(prevApplicationAddress.getCity());
            newApplicationAddress.setState(prevApplicationAddress.getState());
            newApplication.setBusinessName(prevApplication.getBusinessName());
            newApplication.setStatus("draft");
            newApplication.setCategory(selectedCategory);
            newApplication.setAmount(Double.valueOf(breakup.getLoanAmount()));
        }
        if (!StringUtils.isEmpty(requestDTO.getMeta().getLatitude()))
            newApplication.setLatitude(Double.valueOf(requestDTO.getMeta().getLatitude()));
        if (!StringUtils.isEmpty(requestDTO.getMeta().getLongitude()))
            newApplication.setLongitude(Double.valueOf(requestDTO.getMeta().getLongitude()));
        newApplication.setIp(requestDTO.getMeta().getIp());
        newApplicationAddress.setMerchantId(merchant.getId());

        newApplication = creditApplicationDao.save(newApplication);

        newApplicationAddress.setApplicationId(newApplication.getId());
        creditApplicationAddressDao.save(newApplicationAddress);

        if (newApplication.getId() != null) {
            LendingAuditTrial lendingAuditTrial = new LendingAuditTrial();
            lendingAuditTrial.setMerchantId(merchant.getId());
            lendingAuditTrial.setApplicationId(newApplication.getId());
            lendingAuditTrial.setLoanId("");
            lendingAuditTrial.setUserId(Long.parseLong("0"));
            lendingAuditTrial.setNewStatus("draft");
            lendingAuditTrial.setType("APP_STATUS");
            lendingAuditTrialDao.save(lendingAuditTrial);

            if ("AUTO".equalsIgnoreCase(prevApplication.getStatus())) {
                replicateDocumentsForNewApplication(prevApplication, newApplication, merchant, requestDTO.getMeta());
            } else {
                logger.info("Application mode is {}, not replicating documents for new application id {} and merchant id {}", newApplication.getId(), merchant.getId());
            }

            Instant start = Instant.now();
            response = sendOTP(merchant.getMobile());
            Instant end = Instant.now();
            logger.info("Time Taken by GUPSHUP Send OTP API : {} miliseconds", Duration.between(start, end).toMillis());
            response.put("application_id", newApplication.getId());

            creditApplicationService.createMerchantSummarySnapshot(merchant, newApplication, merchantResponseDTO);
        }
        return response;
    }

    private void replicateDocumentsForNewApplication(CreditApplication prevApplication,
                                                     CreditApplication newApplication, BasicDetailsDto merchant, MetaDTO meta) {
        List<MerchantDocumentProof> documentsIdProofList = merchantDocumentProofDao.findByMerchantIdAndOwnerIdAndOwnerType(merchant.getId(), prevApplication.getId(), "LENDING");
        for (MerchantDocumentProof documentsIdProof : documentsIdProofList) {
            MerchantDocumentProof toSaveDocuments = new MerchantDocumentProof();
            toSaveDocuments.setMerchantId(merchant.getId());
            toSaveDocuments.setProofType(documentsIdProof.getProofType());
            toSaveDocuments.setProofFrontSide(documentsIdProof.getProofFrontSide());
            toSaveDocuments.setProofBackSide(documentsIdProof.getProofBackSide());
            toSaveDocuments.setStatus("pending_verification");
            String singleProofDoc = documentsIdProof.getProofFrontSide();
//			if(singleProofDoc == null) {
//				if(documentsIdProof.getProofBackSide() != null) {
//					singleProofDoc = 0;
//				}
//			}

            merchantDocumentProofDao.save(toSaveDocuments);

            if (documentsIdProof.getProofType().equals("selfie")) {
                continue;
            }

            List<MerchantDocumentProofOcr> docKycDetailsList = merchantDocumentProofOcrDao.findByDocumentId(documentsIdProof.getId());

            for (MerchantDocumentProofOcr docKycDetails : docKycDetailsList) {
                MerchantDocumentProofOcr newDocKycDetails = insertIntoDocKycDetails(docKycDetails, toSaveDocuments);
                if ("pancard".equalsIgnoreCase(toSaveDocuments.getProofType())) {
                    List<MerchantDocumentProofRequest> docAuthenticationList = merchantDocumentProofRequestDao.findByDocumentId(documentsIdProof.getId());
                    if (docAuthenticationList != null && docAuthenticationList.size() > 0) {
                        insertIntoDocAuthentication(docAuthenticationList.get(0), newDocKycDetails, toSaveDocuments);
                    }
                }
            }
        }
    }

    private MerchantDocumentProofOcr insertIntoDocKycDetails(MerchantDocumentProofOcr oldDocKycDetails, MerchantDocumentProof documentsIdProof) {
        MerchantDocumentProofOcr docKycDetails = new MerchantDocumentProofOcr();

        docKycDetails.setMerchantId(oldDocKycDetails.getMerchantId());

        docKycDetails.setDocumentId(oldDocKycDetails.getDocumentId());
        docKycDetails.setProofType(documentsIdProof.getProofType());

        docKycDetails.setName(oldDocKycDetails.getName());
        docKycDetails.setDob(oldDocKycDetails.getDob());
        docKycDetails.setGender(oldDocKycDetails.getGender());
        docKycDetails.setFatherName(oldDocKycDetails.getFatherName());
        docKycDetails.setProofNumber(oldDocKycDetails.getProofNumber());
        docKycDetails.setMotherName(oldDocKycDetails.getMotherName());
        docKycDetails.setAddress(oldDocKycDetails.getAddress());
        docKycDetails.setCity(oldDocKycDetails.getCity());
        docKycDetails.setState(oldDocKycDetails.getState());
        docKycDetails.setPincode(oldDocKycDetails.getPincode());
        docKycDetails.setStatus("pending_verification");


        merchantDocumentProofOcrDao.save(docKycDetails);
        return docKycDetails;
    }

    private void insertIntoDocAuthentication(MerchantDocumentProofRequest oldDocAuthentication, MerchantDocumentProofOcr docKycDetails, MerchantDocumentProof documentsIdProof) {
        MerchantDocumentProofRequest docAuthentication = new MerchantDocumentProofRequest();
        docAuthentication.setDocumentId(docKycDetails.getDocumentId());

        docAuthentication.setMerchantId(oldDocAuthentication.getMerchantId());

        docAuthentication.setStatus(oldDocAuthentication.getStatus());

        docAuthentication.setResponse(oldDocAuthentication.getResponse());
        docAuthentication.setStatus(oldDocAuthentication.getStatus());

        merchantDocumentProofRequestDao.save(docAuthentication);
    }

    private Map<String, Object> sendOTP(String mobile) {
        Map<String, Object> finalResponse = new LinkedHashMap<>();
        finalResponse.put("success", false);
        finalResponse.put("otp_flow", false);

        String mobileString = mobile.toString();
        if (mobileString.length() == 12) {
            String message = "BharatPe: {otp} is your OTP to register yourself on BharatPe Merchant App. BharatPe.com";
            Map<String, Object> response = new HashMap<String, Object>();
            response = bharatPeOtpHandler.sendOtp(mobileString, message);
            Boolean isOTPSent = (Boolean) response.get("success");
            String uuid = (String) response.get("uuid");
            logger.info("OTP sent on mobile: {} ", uuid);
            if (isOTPSent) {
                finalResponse.put("success", true);
                finalResponse.put("otp_flow", true);
                finalResponse.put("uuid", uuid);
            }
        }
        return finalResponse;
    }
}