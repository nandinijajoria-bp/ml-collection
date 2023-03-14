package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingGstDetail;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.enums.StatusCheckResponse;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTimeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AbflDataUploadServiceUtil {

    @Autowired
    LenderGatewayFactory lenderGatewayFactory;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    NbfcUtils nbfcUtils;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MerchantService merchantService;

    @Autowired
    LendingGstDao lendingGstDao;

    @Autowired
    LendingMerchantPermissionsDao lendingMerchantPermissionsDao;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Autowired
    LendingMerchantReferencesDao lendingMerchantReferencesDao;

    @Autowired
    LendingMerchantDetailsDao lendingMerchantDetailsDao;

    @Autowired
    LendingRiskVariablesSnapshotDao lendingRiskVariablesSnapshotDao;

    @Autowired
    KycUtils kycUtils;

    @Autowired
    DateTimeUtil dateTimeUtil;

    @Autowired
    ConverterUtils converterUtils;

    public void uploadRegulatoryData(Long applicationId) {
        String response = LenderAssociationStatus.DATA_UPLOAD_COMPLETE.name();
        INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(Lender.ABFL.name());
        RegulatoryApiRequestDto regulatoryApiRequestDto = createRegulatoryPayload(applicationId);
        if (ObjectUtils.isEmpty(regulatoryApiRequestDto)) {
            log.info("empty response for regulatory data for {}", applicationId);
            updateLenderDetailsRecord(applicationId,null, LenderAssociationStatus.DATA_UPLOAD_FAILED.name(),null, null);
            return;
        }
        try {
            RegulatoryApiResponseDto regulatoryApiResponseDto = apiGatewayV3.invokeRegDataUpload(regulatoryApiRequestDto);
            if (ObjectUtils.isEmpty(regulatoryApiResponseDto) || ObjectUtils.isEmpty(regulatoryApiResponseDto.getData())
                    || !StatusCheckResponse.SUCCESS.name().equalsIgnoreCase(regulatoryApiResponseDto.getData().getResponseStatus())) {
                response = LenderAssociationStatus.DATA_UPLOAD_FAILED.name();
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload event for app {}", regulatoryApiRequestDto, e);
            response = LenderAssociationStatus.DATA_UPLOAD_FAILED.name();
        }
        log.info("regulatory upload response {} {}", applicationId, response);
        updateLenderDetailsRecord(applicationId,null, response,null, null);
        return;
    }

    public RegulatoryApiRequestDto createRegulatoryPayload(Long applicationId) {
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        if (!lendingApplicationOptional.isPresent()) {
            log.info("application not found for {}", applicationId);
            return null;
        }
        try {
            MerchantDetailsDto merchantDetailsDto = merchantService.fetchMerchantDetails(lendingApplicationOptional.get().getMerchantId());
            if (ObjectUtils.isEmpty(merchantDetailsDto) || ObjectUtils.isEmpty(merchantDetailsDto.getAddressDetail())) {
                log.info("merchant details not found for {}", lendingApplicationOptional.get().getMerchantId());
                return null;
            }
            LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplicationOptional.get().getId());
            LendingMerchantPermissions lendingMerchantPermissions = lendingMerchantPermissionsDao.findByMerchantId(lendingApplicationOptional.get().getMerchantId());
            List<LendingMerchantReferences> lendingMerchantReferences = lendingMerchantReferencesDao.findByMerchantIdAndApplicationId(lendingApplicationOptional.get().getMerchantId(), lendingApplicationOptional.get().getId());
            LendingMerchantDetails lendingMerchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(lendingApplicationOptional.get().getMerchantId());
            RegulatoryApiRequestDto regulatoryApiRequestDto = RegulatoryApiRequestDto.builder()
                    .applicationId(lendingApplicationOptional.get().getId())
                    .lender(lendingApplicationOptional.get().getLender())
                    .productName("LENDING")
                    .payload(RegulatoryApiRequestDto.Payload.builder()
                            .accountId(lendingApplicationOptional.get().getExternalLoanId())
                            .auditData(ConverterUtils.convertToBase64String(objectMapper.writeValueAsString(
                                    RegulatoryDataDto.builder()
                                            .address(merchantDetailsDto.getAddressDetail().get(0).getAddress())
                                            .businessCategory(lendingMerchantDetails.getBusinessCategory())
                                            .businessSubCategory(lendingMerchantDetails.getBusinessSubCategory())
                                            .customerConsent(lendingMerchantPermissions.getLocationPermissionActive())
                                            .latitude(lendingApplicationOptional.get().getLatitude())
                                            .longitude(lendingApplicationOptional.get().getLongitude())
                                            .professionalDeclaration(lendingGstDetail.getEntityType())
                                            .shopAddress(constructShopAddress(lendingApplicationOptional.get()))
                                            .signedIpAdress(lendingApplicationOptional.get().getIp())
                                            .gstIn(lendingGstDetail.getGstNumber())
                                            .nsdlLog(null)
                                            .nsdlTimestamp(null)
                                            .signedTimestamp(lendingApplicationOptional.get().getAgreementAt())
                                            .smsData(null)
                                            .contactReferences(constructReferencesData(lendingMerchantReferences))
                                            .build()
                            )))
                            .build())
                    .build();
            log.info("regulatory data {}", regulatoryApiRequestDto);
            return regulatoryApiRequestDto;
        } catch (Exception e) {
            log.info("error occurred while processing regulatory data {}", lendingApplicationOptional.get().getId(), e);
        }
        return null;
    }

    public void uploadDocuments(Long applicationId, List<String> docs) {
        String failedDocs = "";
        String currentDocumentStatus = LenderAssociationStatus.DOC_UPLOAD_COMPLETE.name();
        Pair<String,String> response = Pair.of(LenderAssociationStatus.DOC_UPLOAD_COMPLETE.name(),failedDocs);
        List<DocUploadPayload> docUploadPayloadList = createPayload(applicationId,docs, failedDocs, currentDocumentStatus);
        if (ObjectUtils.isEmpty(docUploadPayloadList)){
            log.info("no data found for {}", applicationId);
            updateLenderDetailsRecord(applicationId,LenderAssociationStatus.DOC_UPLOAD_FAILED.name(), null, docs.stream().collect(Collectors.joining(";")), null);
            return;
        }
        log.info("doc payload size {} {}", docUploadPayloadList.size(), applicationId);
        for (DocUploadPayload docUploadPayload: docUploadPayloadList) {
            log.info("{} found for {}",docUploadPayload.getDocType(), applicationId);
        }
        INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(Lender.ABFL.name());
//        List<Future<Pair<String, String>>> docUploadFuture = new ArrayList<>();
//        for (DocUploadPayload docUploadPayload: docUploadPayloadList) {
//            docUploadFuture.add(executorService.submit(() -> uploadDoc(docUploadPayload.getDocUploadApiRequestDto(),apiGatewayV3,docUploadPayload.getDocType())));
//        }
//        for (Future<Pair<String, String>> future: docUploadFuture) {
//            try {
//                Pair<String, String> resp = future.get(10, TimeUnit.SECONDS);
//                if (ObjectUtils.isEmpty(resp) && LenderAssociationStatus.DOC_UPLOAD_FAILED.name().equalsIgnoreCase(resp.getLeft())) {
//                    failedDocs = failedDocs + resp.getRight() + ";";
//                    response = Pair.of(LenderAssociationStatus.DOC_UPLOAD_FAILED.name(),failedDocs);
//                }
//            } catch (Exception e) {
//                log.error("exception occurred while invoking doc upload {} {}", applicationId, e.getMessage());
//                response = Pair.of(LenderAssociationStatus.DOC_UPLOAD_FAILED.name(),failedDocs);
//            }
//        }
        for (DocUploadPayload docUploadPayload: docUploadPayloadList) {
            try {
                if (ObjectUtils.isEmpty(docUploadPayload.getDocUploadApiRequestDto().getPayload().getFileUpload())) {
                    log.info("payload construct incomplete for {} {}", docUploadPayload.getDocType(), applicationId);
                    failedDocs = failedDocs + docUploadPayload.getDocType() + ";";
                    currentDocumentStatus = LenderAssociationStatus.DOC_UPLOAD_FAILED.name();
                    continue;
                }
                Pair<String, String> resp = uploadDoc(docUploadPayload.getDocUploadApiRequestDto(),apiGatewayV3,docUploadPayload.getDocType());
                if (ObjectUtils.isEmpty(resp) && LenderAssociationStatus.DOC_UPLOAD_FAILED.name().equalsIgnoreCase(resp.getLeft())) {
                    failedDocs = failedDocs + resp.getRight() + ";";
                    currentDocumentStatus = LenderAssociationStatus.DOC_UPLOAD_FAILED.name();
                }
            } catch (Exception e) {
                log.error("exception occurred while invoking doc upload {} {} {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
                currentDocumentStatus = LenderAssociationStatus.DOC_UPLOAD_FAILED.name();
            }
        }
        response = Pair.of(currentDocumentStatus,failedDocs);
        updateLenderDetailsRecord(applicationId,response.getLeft(), null, response.getRight(), null);
    }

    public Pair<String, String> uploadDoc(DocUploadApiRequestDto docUploadApiRequestDto, INbfcLenderGateway apiGatewayV3, String docType) {
        try {
            DocUploadApiResponse docUploadApiResponse = apiGatewayV3.invokeDocUpload(docUploadApiRequestDto);
            if (ObjectUtils.isEmpty(docUploadApiResponse) || ObjectUtils.isEmpty(docUploadApiResponse.getData()) ||  !StatusCheckResponse.SUCCESS.name().equalsIgnoreCase(docUploadApiResponse.getData().getResponseStatus())) {
                return Pair.of(LenderAssociationStatus.DOC_UPLOAD_FAILED.name(),docType);
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload event for app {} {}", docUploadApiRequestDto.getApplicationId(), e, Arrays.asList(e.getStackTrace()));
            return Pair.of(LenderAssociationStatus.DOC_UPLOAD_FAILED.name(),docType);
        }
        return  Pair.of(LenderAssociationStatus.DOC_UPLOAD_COMPLETE.name(),"");
    }

    public String constructShopAddress(LendingApplication lendingApplication) {
        return (ObjectUtils.isEmpty(lendingApplication.getShopNumber()) ? "" : lendingApplication.getShopNumber()) + " " +
                (ObjectUtils.isEmpty(lendingApplication.getStreetAddress()) ? "" : lendingApplication.getStreetAddress()) + " " +
                (ObjectUtils.isEmpty(lendingApplication.getLandmark()) ? "" : lendingApplication.getLandmark()) + " " +
                (ObjectUtils.isEmpty(lendingApplication.getCity()) ? "" : lendingApplication.getCity()) + " " +
                (ObjectUtils.isEmpty(lendingApplication.getState()) ? "" : lendingApplication.getState()) + " " +
                (ObjectUtils.isEmpty(lendingApplication.getPincode()) ? "" : lendingApplication.getPincode());
    }

    public String constructReferencesData (List<LendingMerchantReferences> lendingMerchantReferencesList) {
        String data = "";
        if (ObjectUtils.isEmpty(lendingMerchantReferencesList)){
            return data;
        }
        for (LendingMerchantReferences lendingMerchantReferences : lendingMerchantReferencesList) {
            data += lendingMerchantReferences.getReferenceName() + ":" + lendingMerchantReferences.getReferenceNumber() + ";";
        }
        return data;
    }

    public void updateLenderDetailsRecord(Long applicationId, String docUploadStatus, String dataUploadStatus, String failedUploadDocs, String digitalDataUploadStatus) {
        try {
            log.info("app {}, docUploadStatus {}, dataUploadStatus {}, failedUploadDocs {}, digitalDataUploadStatus {}", applicationId,  docUploadStatus,  dataUploadStatus,  failedUploadDocs,  digitalDataUploadStatus);
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId, Status.ACTIVE.name());
            lendingApplicationLenderDetails.setDocUploadStatus(ObjectUtils.isEmpty(docUploadStatus)? lendingApplicationLenderDetails.getDocUploadStatus(): docUploadStatus);
            lendingApplicationLenderDetails.setDataUploadStatus(ObjectUtils.isEmpty(dataUploadStatus)? lendingApplicationLenderDetails.getDataUploadStatus(): dataUploadStatus);
            lendingApplicationLenderDetails.setFailedUpload(ObjectUtils.isEmpty(failedUploadDocs)? lendingApplicationLenderDetails.getFailedUpload(): failedUploadDocs);
            lendingApplicationLenderDetails.setDigitalDataUploadStatus(ObjectUtils.isEmpty(digitalDataUploadStatus)? lendingApplicationLenderDetails.getDigitalDataUploadStatus(): digitalDataUploadStatus);
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        } catch (Exception e) {
            log.error("exception occurred while saving data to table {} {} {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    public  List<DocUploadPayload> createPayload(Long applicationId, List<String> docTypes, String failedDocs, String currentDocumentStatus) {
        List<DocUploadPayload> docUploadPayloadList = new ArrayList<>();
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        if (!lendingApplicationOptional.isPresent()) {
            log.info("application not found for {}", applicationId);
            return docUploadPayloadList;
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(applicationId, Status.ACTIVE.name());
        if (ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("lending application lender details not found for {}", applicationId);
            return docUploadPayloadList;
        }
        failedDocs = ObjectUtils.isEmpty(lendingApplicationLenderDetails.getFailedUpload()) ? failedDocs : lendingApplicationLenderDetails.getFailedUpload();
        currentDocumentStatus = ObjectUtils.isEmpty(lendingApplicationLenderDetails.getDocUploadStatus()) ? currentDocumentStatus : lendingApplicationLenderDetails.getDocUploadStatus();
        log.info("fetching document data for {}", applicationId);
        LendingApplication lendingApplication = lendingApplicationOptional.get();
        LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
        for (String docType: docTypes) {
            try {
                String currDate = String.valueOf(new Date().getTime());
                String requestId = lendingApplication.getId() + currDate.substring(currDate.length() - 5);
                DocUploadApiRequestDto docUploadApiRequestDto = DocUploadApiRequestDto.builder()
                        .applicationId(lendingApplication.getId())
                        .productName("LENDING")
                        .lender(lendingApplication.getLender())
                        .payload(DocUploadApiRequestDto.Payload.builder()
                                .accountId(lendingApplication.getExternalLoanId())
                                .cccId(lendingApplicationLenderDetails.getCccId())
                                .customerId(lendingApplicationLenderDetails.getCccId())
                                .docType(docType)
                                .requestId(requestId)
                                .fileName(docType)
                                .build())
                        .build();
                DocUploadApiRequestDto.Payload payload = docUploadApiRequestDto.getPayload();
                if ("KFS".equalsIgnoreCase(docType) || "SANCTION_AGREEMENT".equalsIgnoreCase(docType) || "WELCOME_LETTER".equalsIgnoreCase(docType)) {
                    if (ObjectUtils.isEmpty(lendingKfs)) {
                        log.info("kfs not found for {}", applicationId);
                        continue;
                    }
                    payload.setFileName(docType + "_" + lendingApplication.getId() + ".pdf");
                    String docUrl = null;
                    if ("SANCTION_AGREEMENT".equalsIgnoreCase(docType)) {
                        docUrl = lendingKfs.getSanctionLoanAgreementDocUrl();
                    } else if ("KFS".equalsIgnoreCase(docType)) {
                        docUrl = lendingKfs.getKfsDocUrl();
                    } else if ("WELCOME_LETTER".equalsIgnoreCase(docType)) {
                        docUrl = lendingKfs.getWelcomeDocUrl();
                    }
                    payload.setFileUpload(ConverterUtils.convertPreSignedUrlToBase64String(docUrl));
                } else if ("SHOP-FRONT".equalsIgnoreCase(docType) || "SHOP-STOCK".equalsIgnoreCase(docType)) {
                    LendingShopDocuments lendingShopDocument = lendingShopDocumentsDao.findTop1ByMerchantIdAndApplicationIdAndProofTypeOrderByIdDesc(lendingApplication.getMerchantId(), lendingApplication.getId(), docType);
                    log.info("lending shop doc {} {}",docType, lendingShopDocument);
                    if (ObjectUtils.isEmpty(lendingShopDocument) || ObjectUtils.isEmpty(lendingShopDocument.getProofFrontSide())) {
                        log.info("shop doc not found for {} {}", docType, applicationId);
                        continue;
                    }
                    payload.setFileName(docType + "_" + lendingApplication.getId() + ".jpeg");
                    payload.setFileUpload(ConverterUtils.convertPreSignedUrlToBase64String(s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(lendingShopDocument.getProofFrontSide(),bucket)));
                }
                docUploadApiRequestDto.setPayload(payload);
                docUploadPayloadList.add(DocUploadPayload.builder().docType(docType).docUploadApiRequestDto(docUploadApiRequestDto).build());
                log.info("payload size {} {}", docUploadPayloadList.size(), applicationId);
            } catch (Exception e) {
                log.error("some issue occurred while creating doc payload {} {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
            }
        }
        return docUploadPayloadList;
    }

    public void uploadDigitalData(Long applicationId) {
        String response = LenderAssociationStatus.DGTL_UPLOAD_COMPLETE.name();
        INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(Lender.ABFL.name());
        DigitalDataUploadRequest digitalDataUploadRequest = createDigitalDataPayload(applicationId);
        if (ObjectUtils.isEmpty(digitalDataUploadRequest)) {
            updateLenderDetailsRecord(applicationId,null, null,null, LenderAssociationStatus.DGTL_UPLOAD_FAILED.name());
            return;
        }
        try {
            DigitalDataUploadResponse digitalDataUploadResponse = apiGatewayV3.invokeDigitalDataUpload(digitalDataUploadRequest);
            if (ObjectUtils.isEmpty(digitalDataUploadResponse) || ObjectUtils.isEmpty(digitalDataUploadResponse.getData()) ||
                !StatusCheckResponse.SUCCESS.name().equalsIgnoreCase(digitalDataUploadResponse.getData().getResponseStatus())
            ) {
                response = LenderAssociationStatus.DGTL_UPLOAD_FAILED.name();
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking digital data upload event for app {}", digitalDataUploadRequest, e);
            response = LenderAssociationStatus.DGTL_UPLOAD_FAILED.name();
        }
        log.info("digital upload response {} {}", applicationId, response);
        updateLenderDetailsRecord(applicationId,null, null,null, response);
    }

    public DigitalDataUploadRequest createDigitalDataPayload(Long applicationId) {
        Optional<LendingApplication> lendingApplicationOptional = lendingApplicationDao.findById(applicationId);
        if (!lendingApplicationOptional.isPresent()) {
            log.info("application not found for {}", applicationId);
            return null;
        }
        try {
            LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplicationOptional.get().getId());
            LendingRiskVariablesSnapshot lendingRiskVariablesSnapshot = lendingRiskVariablesSnapshotDao.findByApplicationId(lendingApplicationOptional.get().getId());
            LendingMerchantDetails lendingMerchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(lendingApplicationOptional.get().getMerchantId());
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(lendingApplicationOptional.get().getId(),Status.ACTIVE.name());
            CKycResponseDto cKycResponseDto = kycUtils.getKycData(lendingApplicationOptional.get().getMerchantId());
            String mobileNumber = ObjectUtils.isEmpty(cKycResponseDto.getMobile()) ? "0" : cKycResponseDto.getMobile().substring(2);
            DigitalDataUploadRequest digitalDataUploadRequest = DigitalDataUploadRequest.builder()
                    .applicationId(lendingApplicationOptional.get().getId())
                    .lender(lendingApplicationOptional.get().getLender())
                    .productName("LENDING")
                    .payload(DigitalDataUploadRequest.Payload.builder()
                            .category(converterUtils.parseData(lendingMerchantDetails.getBusinessCategory()))
                            .companyCategory(lendingMerchantDetails.getBusinessSubCategory())
                            .cabTransactionData(lendingGstDetail.getGstNumber())
                            .courseTenure(lendingRiskVariablesSnapshot.getVintage().intValue())
                            .bureauScore(lendingRiskVariablesSnapshot.getBureauScore().intValue())
                            .accountId(lendingApplicationOptional.get().getExternalLoanId())
                            .cccId(lendingApplicationLenderDetails.getCccId())
                            .addressLine2(lendingApplicationOptional.get().getLatitude().replace(".", " ") + "," + lendingApplicationOptional.get().getLongitude().replace(".", " "))
                            .subIndustryType(lendingGstDetail.getShopType())
                            .mobileNumber(new BigInteger(mobileNumber))
                            .incomeDocumentProof("Yes")
                            .build())
                    .build();
            log.info("digital data {}", digitalDataUploadRequest);
            return digitalDataUploadRequest;
        } catch (Exception e) {
            log.info("error occurred while processing digital data {}", lendingApplicationOptional.get().getId(), e);
        }
        return null;
    }

    @Async
    public void pushDataToNbfc(Long applicationId) {
        try {
            uploadRegulatoryData(applicationId);
        } catch (Exception e) {
            log.error("error occurred while uploading regulatory data {} {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()) );
        }
        try {
            uploadDigitalData(applicationId);
        } catch (Exception e) {
            log.error("error occurred while uploading digital data {} {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()) );
        }
        try {
            uploadDocuments(applicationId, Arrays.asList("KFS", "SANCTION_AGREEMENT", "SHOP-FRONT", "SHOP-STOCK"));
        } catch (Exception e) {
            log.error("error occurred while uploading docs data {} {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()) );
        }
    }
}
