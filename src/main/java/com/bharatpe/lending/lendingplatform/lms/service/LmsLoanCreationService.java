package com.bharatpe.lending.lendingplatform.lms.service;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingPaymentSchedule;
import com.bharatpe.lending.common.dao.LendingMerchantReferencesDao;
import com.bharatpe.lending.common.dao.LmsLoanStatusDao;
import com.bharatpe.lending.common.dto.MerchantNachDetailsResponseDTO;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.service.merchant.dto.BankDetailsDto;
import com.bharatpe.lending.dto.PanFetchKYCResponseDto;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.handlers.KycHandler;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.lendingplatform.document.service.DocumentService;
import com.bharatpe.lending.lendingplatform.lms.client.LendingPlatformHttpClient;
import com.bharatpe.lending.lendingplatform.lms.constant.Constants;
import com.bharatpe.lending.lendingplatform.lms.constant.ErrorResponseCodeGroupMappings;
import com.bharatpe.lending.lendingplatform.lms.consumer.service.LmsCreateLoanFailureCallback;
import com.bharatpe.lending.lendingplatform.lms.dto.request.CreateLoanRequest;
import com.bharatpe.lending.lendingplatform.lms.dto.response.ApiResponse;
import com.bharatpe.lending.lendingplatform.lms.dto.response.CreateLoanResponse;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.service.APIGatewayService;
import com.bharatpe.lending.service.LendingApplicationKycDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.math.BigDecimal;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ApiEndPointConstants.CREATE_LOAN;
import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.Consumer.CUSTOM_LMS_ERRORS;
import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.Consumer.LMS_ERRORS;
import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ErrorStatusCode.BAD_REQUEST;
import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.ErrorStatusCode.INTERNAL_SERVER_ERROR;
import static com.bharatpe.lending.lendingplatform.lms.constant.Constants.PROCESSING_FEE;

import org.springframework.transaction.annotation.Transactional;


@Service
@Slf4j
public class LmsLoanCreationService {

    @Autowired
    private LendingMerchantReferencesDao lendingMerchantReferencesDao;

    @Autowired
    private LendingPlatformHttpClient lendingPlatformHttpClient;

    @Autowired
    private LmsLoanDetailsService lmsLoandetailsservice;

    @Autowired
    private S3BucketHandler s3BucketHandler;

    @Autowired
    private APIGatewayService apiGatewayService;

    @Autowired
    private LmsLoanStatusDao lmsLoanStatusDao;

    @Autowired
    private LmsCreateLoanFailureCallback lmsCreateLoanFailureCallback;
    @Autowired
    private DocumentService documentService;
    @Autowired
    private LendingApplicationKycDetailsService lendingApplicationKycDetailsService;
    @Autowired
    private KycHandler kycHandler;


    public boolean processLoanRequest(LendingApplication lendingApplication,
                                   LendingPaymentSchedule lendingPaymentSchedule,
                                   Map<String, Object> disbursalResponseMap, Date disbursalDate) {
        try {
            CreateLoanRequest createLoanRequest = mapCreateLoanRequest(lendingApplication, lendingPaymentSchedule, disbursalDate);
            ApiResponse<CreateLoanResponse> createLoanResponse = lendingPlatformHttpClient.sendPostRequest(
                    CREATE_LOAN, createLoanRequest, CreateLoanResponse.class);

            updateLmsLoanStatus(createLoanResponse, lendingApplication.getExternalLoanId(), disbursalResponseMap);

            if (Objects.nonNull(createLoanResponse) && (createLoanResponse.isSuccess() || ("1006").equalsIgnoreCase(createLoanResponse.getError().getErrorStatusCode()))) {
                log.info("Loan posted successfully. BP Loan ID: {}", lendingApplication.getExternalLoanId());
                return true;
            }

            return handleFailureResponse(createLoanResponse, lendingApplication.getExternalLoanId());
        } catch (Exception e) {
            log.error("Exception occurred while processing loan request: {}", e.getMessage(), e);
            lmsCreateLoanFailureCallback.sendLoanToOldFlow(lendingApplication.getExternalLoanId());
            return false;
        }
    }

    private boolean handleFailureResponse(ApiResponse<CreateLoanResponse> createLoanResponse, String bpLoanId) {
        if((BAD_REQUEST).equals(createLoanResponse.getError().getErrorStatusCode())){
            log.info("Loan creation failed for bploanId:{}, sending loan to existing flow", bpLoanId);
            lmsCreateLoanFailureCallback.sendLoanToOldFlow(bpLoanId);
            return false;
        }
        if((INTERNAL_SERVER_ERROR).equals(createLoanResponse.getError().getErrorStatusCode())){
            log.info("Loan creation failed for bploanId:{}, received internal server error", bpLoanId);
            return false;
        }

        String errorCode = createLoanResponse.getError().getErrorStatusCode();
        String errorGroup = ErrorResponseCodeGroupMappings.findGroupForErrorCode(errorCode);

        if(!createLoanResponse.isSuccess() && !(CUSTOM_LMS_ERRORS).equalsIgnoreCase(errorGroup)){
            log.info("Loan creation failed for bploanId:{}, sending loan to existing flow", bpLoanId);
            lmsCreateLoanFailureCallback.sendLoanToOldFlow(bpLoanId);
            return false;
        }
        return false;
    }

    private String isLoanStatusUpdate(ApiResponse<CreateLoanResponse> createLoanResponse, String bpLoanId) {
        if (ObjectUtils.isEmpty(createLoanResponse)) {
            log.warn("Loan request failed: createLoanResponse is null or empty.");
            return "FAILED";
        }

        if (createLoanResponse.isSuccess()) {
            return "PENDING";
        }

        String errorCode = createLoanResponse.getError().getErrorStatusCode();
        String errorGroup = ErrorResponseCodeGroupMappings.findGroupForErrorCode(errorCode);

        if ((CUSTOM_LMS_ERRORS).equalsIgnoreCase(errorGroup)) {
            return "PENDING";
        }

        if ((INTERNAL_SERVER_ERROR).equalsIgnoreCase(createLoanResponse.getError().getErrorStatusCode())) {
            log.info("Loan creation failed for bploanId:{}, received internal server error", bpLoanId);
            return "INIT";
        }

        if ((LMS_ERRORS).equalsIgnoreCase(errorGroup)) {
            return "FAILED";
        }

        // Handle the case when isSuccess is false
        if ((BAD_REQUEST).equalsIgnoreCase(createLoanResponse.getError().getErrorStatusCode())) {
            log.info("Loan request failed: Error object is null or empty in createLoanResponse.");
            return "FAILED";
        }

        log.warn("Loan request failed: Error code '{}' does not belong to the CUSTOM_LMS_ERRORS group.", errorCode);
        return "FAILED";
    }

    private void updateLmsLoanStatus(ApiResponse<CreateLoanResponse> createLoanResponse, String bpLoanId, Map<String, Object> disbursalResponseMap) {
        LmsLoanStatus lmsLoanStatus = lmsLoanStatusDao.findLatestByBpLoanId(bpLoanId);
        lmsLoanStatus.setStatus(isLoanStatusUpdate(createLoanResponse,bpLoanId));
        lmsLoanStatus.setDisbursalResponse(disbursalResponseMap);
        lmsLoanStatus.setUpdatedAt(new java.util.Date());
        lmsLoanStatusDao.save(lmsLoanStatus);
    }

    private CreateLoanRequest mapCreateLoanRequest(LendingApplication lendingApplication,
                                                   LendingPaymentSchedule lendingPaymentSchedule, Date disbursalDate) throws FileNotFoundException {
        log.info("mapping create loan request. BP Loan ID: {}", lendingApplication.getExternalLoanId());

        LendingApplicationLenderDetails lenderDetails = lmsLoandetailsservice.getLenderDetails(lendingApplication.getId(), lendingApplication.getLender());
        log.info("got la. merchnat ID: {}", lenderDetails.getApplicationId());
        Double annualRoi = lenderDetails.getAnnualRoi();

        BankDetailsDto merchantBankDetail = lmsLoandetailsservice.getMerchantBankDetails(lendingApplication.getMerchantId());
        CKycResponseDto cKycResponseDto = lmsLoandetailsservice.getKycData(lendingApplication.getMerchantId());
        String selfieUrl = !ObjectUtils.isEmpty(cKycResponseDto) && !ObjectUtils.isEmpty(cKycResponseDto.getSelfieString())
                ? cKycResponseDto.getSelfieString()
                : null;
        if (!ObjectUtils.isEmpty(selfieUrl)) {
            lendingApplicationKycDetailsService.copySelfieFromPresignedUrlToS3(cKycResponseDto.getSelfieString(), lenderDetails.getApplicationId());
        }
        LendingKfs lendingKfs = lmsLoandetailsservice.getLendingKfs(lendingApplication.getId());

        LendingApplicationKycDetails lendingApplicationKycDetails = lmsLoandetailsservice.getKycDetails(lendingApplication.getId());

        CreateLoanRequest.LoanDetails loanDetails = getLoanDetails(lendingApplication, annualRoi, disbursalDate);
        CreateLoanRequest.CustomerDetails customerDetails = getCustomerDetails(lendingApplication, merchantBankDetail, cKycResponseDto, lendingPaymentSchedule, lendingApplicationKycDetails);
        CreateLoanRequest.NBFCDetails nbfcDetails = getNbfcDetails(lendingApplication.getNbfcId(), lenderDetails.getLeadId());
        CreateLoanRequest.MandateDetails mandateDetails = getMandateDetails(lendingApplication);
        ArrayList<CreateLoanRequest.LoanDocuments> loanDocumentsList = getLoanDocuments(lendingKfs, lendingApplication);
        ArrayList<CreateLoanRequest.CustomerReferences> customerReferences = getCustomerReferences(lendingApplication);
        ArrayList<CreateLoanRequest.ChargesList> chargesList = getCharges(lendingApplication);

        log.info("All loan creation objects have been successfully mapped. BP Loan ID: {}", lendingApplication.getExternalLoanId());

        return CreateLoanRequest.builder()
                .bpLoanId(lendingApplication.getExternalLoanId())
                .applicationId(lendingApplication.getId().toString())
                .productName(lendingApplication.getLender())
                .loanDetails(loanDetails)
                .customerDetails(customerDetails)
                .nbfcDetails(nbfcDetails)
                .loanDocuments(loanDocumentsList)
                .customerReferences(customerReferences)
                .chargesList(chargesList)
                .mandateDetails(mandateDetails)
                .build();
    }



    private ArrayList<CreateLoanRequest.ChargesList> getCharges(LendingApplication lendingApplication) {
        log.info("Sending processing fee={} for bpLoanId:{}", lendingApplication.getProcessingFee(), lendingApplication.getExternalLoanId());
        return new ArrayList<>(Collections.singletonList(
                CreateLoanRequest.ChargesList.builder()
                        .chargeAmount(BigDecimal.valueOf(lendingApplication.getProcessingFee()))
                        .chargeName(PROCESSING_FEE)
                        .build()
        ));
    }

    private CreateLoanRequest.LoanDetails getLoanDetails(LendingApplication lendingApplication,
                                                         Double annualRoi,Date disbursalDate) {
        try {
            log.info("Disbursed Timestamp {}", disbursalDate);
            LocalDate disburseLocalDate = disbursalDate.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();

            LocalDate currentDate = LocalDate.now(ZoneId.systemDefault());

            return CreateLoanRequest.LoanDetails.builder()
                    .loanAmount(BigDecimal.valueOf(lendingApplication.getLoanAmount()))
                    .ediAmount(lendingApplication.getEdi().intValue())
                    .disbursedAmount(BigDecimal.valueOf(lendingApplication.getLoanAmount()))
                    .disburseDate(java.sql.Date.valueOf(disburseLocalDate))
                    .instrumentDate(java.sql.Date.valueOf(currentDate))
                    .roi(BigDecimal.valueOf(annualRoi))
                    .loanTenure(lendingApplication.getPayableDays().intValue())
                    .firstDueDate(java.sql.Date.valueOf(disburseLocalDate.plusDays(1)))
                    .build();
        } catch(Exception e) {
            log.error("Error while mapping loan details for bpLoanId: {}", lendingApplication.getExternalLoanId(), e);
            throw new RuntimeException("Error while mapping loan details: " + e.getMessage(), e);
        }

    }

    private CreateLoanRequest.CustomerDetails getCustomerDetails(LendingApplication lendingApplication,
                                                                 BankDetailsDto merchantBankDetail,
                                                                 CKycResponseDto cKycResponseDto,
                                                                 LendingPaymentSchedule lendingPaymentSchedule,
                                                                 LendingApplicationKycDetails lendingApplicationKycDetails) {

        String formattedDob = null;
        PanFetchKYCResponseDto panFetchKYCResponse = kycHandler.panFetch(cKycResponseDto.getPanNumber(), lendingApplication.getMerchantId());
        try {
            String panDob = panFetchKYCResponse.getData().getVerifiedDob();
            String cKycDob = cKycResponseDto.getDob();
            log.info("cKycDob: {}, panDob: {}", cKycDob, panDob);
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy");
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
            formattedDob = outputFormat.format(inputFormat.parse( !ObjectUtils.isEmpty(cKycDob) ? cKycDob : panDob));
        } catch (ParseException e) {
            log.error("Error parsing date of birth: {}", e.getMessage(), e);
        }

        String customerCity = cKycResponseDto.getCity();
        String customerState = cKycResponseDto.getState();
        String customerPinCode = ObjectUtils.isEmpty(cKycResponseDto.getPincode())
                ? panFetchKYCResponse.getData().getNonNsdladdress().getPincode()
                : cKycResponseDto.getPincode();

        if (Objects.nonNull(customerPinCode) && !customerPinCode.trim().isEmpty()) {
            try {
                LendingPincodes lendingPincodes = lmsLoandetailsservice.getCustomerAddressDetails(Integer.parseInt(customerPinCode.trim()));
                if (!ObjectUtils.isEmpty(lendingPincodes)) {
                    log.info("Pincode available in lending_pincodes table for bpLoanId: {}", lendingApplication.getExternalLoanId());
                    customerCity = lendingPincodes.getCity();
                    customerState = lendingPincodes.getState();
                }
            } catch (NumberFormatException e) {
                log.error("Invalid customerPinCode: {}", customerPinCode, e);
            } catch (Exception e) {
                log.error("Error fetching customer address details for pinCode: {}", customerPinCode, e);
            }
        }

        String customerName = cKycResponseDto.getName().replaceAll("[^a-zA-Z0-9 ]", "");

        String gender = (!ObjectUtils.isEmpty(cKycResponseDto.getGender()) && cKycResponseDto.getGender().toUpperCase().startsWith("M")) ? "M" :
                        (!ObjectUtils.isEmpty(cKycResponseDto.getGender()) && cKycResponseDto.getGender().toUpperCase().startsWith("F")) ? "F" : "O";

        return CreateLoanRequest.CustomerDetails.builder()
                .customerId(String.valueOf(lendingApplication.getMerchantId()))
                .customerAddress(cKycResponseDto.getAddress())
                .customerCity(customerCity)
                .customerState(customerState)
                .customerPinCode(Long.parseLong(cKycResponseDto.getPincode()))
                .customerAadharNo(cKycResponseDto.getAadharNumber())
                .customerDOB(formattedDob)
                .customerGender(gender)
                .customerBankAccNo(merchantBankDetail.getAccountNumber())
                .customerBankBranch(merchantBankDetail.getBankName())
                .customerBankIFSC(merchantBankDetail.getIfsc())
                .customerFatherName(
                        ObjectUtils.isEmpty(lendingApplicationKycDetails) || ObjectUtils.isEmpty(lendingApplicationKycDetails.getFatherName())
                                ? "FO " + customerName
                                : lendingApplicationKycDetails.getFatherName().replaceAll("[^a-zA-Z0-9 ]", "")
                )
                .customerMobileNo(cKycResponseDto.getMobile().length() > 10
                        ? cKycResponseDto.getMobile().substring(cKycResponseDto.getMobile().length() - 10)
                        : cKycResponseDto.getMobile())
                .customerName(
                        customerName.contains(" ")
                                ? customerName
                                : customerName + " " + customerName
                )
                .customerPAN(cKycResponseDto.getPanNumber())
                .shopAddress(lendingApplication.getStreetAddress())
                .shopCity(lendingApplication.getCity())
                .shopPinCode(lendingApplication.getPincode())
                .shopState(lendingApplication.getState())
                .build();
    }

    private CreateLoanRequest.NBFCDetails getNbfcDetails(String nbfcId, String leadId) {
        //sending dummy nbfc bank details to 1LMS system
        return CreateLoanRequest.NBFCDetails.builder()
                .nbfcId(nbfcId)
                .leadId(leadId)
                .nbfcBankAcc("10150146205")
                .nbfcBankIFSC("IDFB0020145")
                .build();
    }

    private ArrayList<CreateLoanRequest.LoanDocuments> getLoanDocuments(LendingKfs lendingKfs, LendingApplication lendingApplication){

        //Generating S3 Link for Shop pictures
        List<LendingShopDocuments> lendingShopDocuments = lmsLoandetailsservice.getShopFrontImage(lendingApplication.getMerchantId(), lendingApplication.getId());
        log.info("generating shop picture for applicationId {}", lendingKfs.getApplicationId());

        Map<String, String> proofImages = new HashMap<>();
        proofImages.put("shop-front", null);
        proofImages.put("shop-stock", null);

        for (LendingShopDocuments lendingShopDocument : lendingShopDocuments) {
            String proofType = lendingShopDocument.getProofType();
            if (proofImages.containsKey(proofType)) {
                proofImages.put(proofType,
                        !StringUtils.isEmpty(lendingShopDocument.getProofFrontSide())
                                ? lendingShopDocument.getProofFrontSide()
                                : null
                );
            }
        }
        String proofFrontSide = proofImages.get("shop-front");
        String proofStockSide = proofImages.get("shop-stock");

        String shopFrontImage = documentService.generateDocUrl(proofFrontSide);
        String shopStockImage = documentService.generateDocUrl(proofStockSide);
        log.info("Fetched shop picture URL - {}, {}", shopFrontImage, shopStockImage);

        return new ArrayList<>(Arrays.asList(
                CreateLoanRequest.LoanDocuments.builder()
                        .docType(Constants.DocumentNamesConstants.SHOP_FRONT_PHOTO)
                        .docUrl(shopFrontImage)
                        .build(),
                CreateLoanRequest.LoanDocuments.builder()
                        .docType(Constants.DocumentNamesConstants.SHOP_STOCK_PHOTO)
                        .docUrl(shopStockImage)
                        .build(),
                CreateLoanRequest.LoanDocuments.builder()
                        .docType(Constants.DocumentNamesConstants.CUSTOMER_SELFIE)
                        .docUrl(documentService.generateDocUrl(
                                lendingApplicationKycDetailsService.getSelfieImage(lendingApplication.getId())))
                        .build(),
                CreateLoanRequest.LoanDocuments.builder()
                        .docType(Constants.DocumentNamesConstants.KEY_FACT_STATEMENT)
                        .docUrl(documentService.generateDocUrl(lendingKfs.getKfsDocFile()))
                        .build(),
                CreateLoanRequest.LoanDocuments.builder()
                        .docType(Constants.DocumentNamesConstants.LOAN_AGREEMENT)
                        .docUrl(documentService.generateDocUrl(lendingKfs.getSanctionLoanAgreementDocFile()))
                        .build()
        ));
    }

    //Currently sending just 1 reference details to 1LMS
    private ArrayList<CreateLoanRequest.CustomerReferences> getCustomerReferences(LendingApplication lendingApplication) {
        log.info("Inside fetch merchant reference details method for bpLoanId:{}",lendingApplication.getExternalLoanId());
        return lendingMerchantReferencesDao.findByMerchantIdAndApplicationId(
                        lendingApplication.getMerchantId(), lendingApplication.getId())
                .stream()
                .findFirst()
                .map(ref -> {
                    String[] nameParts = ref.getReferenceName().trim().split("\\s+", 2);
                    String firstName = nameParts[0];
                    String lastName = nameParts.length > 1 ? nameParts[1] : firstName;
                    log.info("REFERENCES - {}, {}", firstName, lastName);
                    return CreateLoanRequest.CustomerReferences.builder()
                            .referenceFirstName(firstName)
                            .referenceLastName(lastName)
                            .referenceContactNumber(ref.getReferenceNumber())
                            .referenceRelation(ref.getInferredRelation())
                            .build();
                })
                .map(ref -> {
                    ArrayList<CreateLoanRequest.CustomerReferences> list = new ArrayList<>();
                    list.add(ref);
                    return list;
                })
                .orElseGet(ArrayList::new);
    }

    //Need to uncomment below function once we can send multiple reference with same relation to 1LMS
//    private ArrayList<CreateLoanRequest.CustomerReferences> getCustomerReferences(LendingApplication lendingApplication) {
//        return lendingMerchantReferencesDao.findByMerchantIdAndApplicationId(
//                        lendingApplication.getMerchantId(), lendingApplication.getId())
//                .stream()
//                .map(ref -> {
//                    String[] nameParts = ref.getReferenceName().trim().split("\\s+", 2);
//                    String firstName = nameParts[0];
//                    String lastName = nameParts.length > 1 ? nameParts[1] : firstName;
//                    log.info("REFERNCES- {}, {}",firstName, lastName);
//                    return CreateLoanRequest.CustomerReferences.builder()
//                            .referenceFirstName(firstName)
//                            .referenceLastName(lastName)
//                            .referenceContactNumber(ref.getReferenceNumber())
//                            .referenceRelation(ref.getInferredRelation())
//                            .build();
//                })
//                .collect(Collectors.toCollection(ArrayList::new));
//    }

    private CreateLoanRequest.MandateDetails getMandateDetails(LendingApplication lendingApplication) {
        log.info("Fetching mandate details for bpLoanId: {}", lendingApplication.getExternalLoanId());
        try{
            MerchantNachDetailsResponseDTO merchantNachDetailsResponseDTO = lmsLoandetailsservice.getMandateDetails(lendingApplication);
            return CreateLoanRequest.MandateDetails.builder()
                    .mandateAmount(String.valueOf(merchantNachDetailsResponseDTO.getNachAmount()))
                    .mandateEndDate(new SimpleDateFormat("yyyy-MM-dd").parse("2027-11-01"))
                    .mandateStartDate(merchantNachDetailsResponseDTO.getStartDate())
                    .mandateId(merchantNachDetailsResponseDTO.getMandateId())
                    .umrn(merchantNachDetailsResponseDTO.getProviderUmrn())
                    .bankIFSCCode(merchantNachDetailsResponseDTO.getIfscCode())
                    .bankName(merchantNachDetailsResponseDTO.getBankName())
                    .build();
        }catch (Exception e){
            log.info("mandate details not found ", e);
        }
        return null;
    }
}
