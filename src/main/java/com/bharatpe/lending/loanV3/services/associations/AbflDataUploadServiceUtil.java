package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingGstDetail;
import com.bharatpe.lending.common.dao.*;
import com.bharatpe.lending.common.entity.*;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.enums.StatusCheckResponse;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.common.service.merchant.dto.MerchantDetailsDto;
import com.bharatpe.lending.common.service.merchant.service.MerchantService;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.constant.KfsConstants;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV2.service.LendingApplicationServiceV2;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFUpdateLeadRequestDTO;
import com.bharatpe.lending.loanV3.factory.LenderGatewayFactory;
import com.bharatpe.lending.loanV3.revamp.response.LoanDashboardApiVersion;
import com.bharatpe.lending.loanV3.revamp.services.LoanDashboardService;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import com.bharatpe.lending.loanV3.utils.ConverterUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.bharatpe.lending.util.FileUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfCopy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTimeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @Autowired
    LendingApplicationKycDetailsDao lendingApplicationKycDetailsDao;

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

    @Autowired
    @Lazy
    LendingApplicationServiceV2 lendingApplicationServiceV2;

    @Autowired
    private LoanDashboardService loanDashboardService;

    @Value("${abfl.lender.doc.rollout.datetime:}")
    String lenderDocRolloutDateTime;


    @Autowired
    ABFLDigiSignService abflDigiSignService;

    @Value("${abfl.topup.lender.doc.rollout.datetime:}")
    String lenderTopupDocRolloutDateTime;

    @Value("${lender.doc.generate.enabled.lenders:}")
    String lenderDocGenerateEnabledLenders;

    @Value("${lender.doc.generate.topup.enabled.lenders:}")
    String lenderDocGenerateTopUpEnabledLenders;

    private static final String CURRENT_DIR = Paths.get("").toAbsolutePath().toString();

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
            LoanDashboardApiVersion loanDashboardApiVersion = loanDashboardService.getLoanDashboardApiVersion(merchantDetailsDto.getMerchantDetail().getId());
            LendingGstDetail lendingGstDetail = lendingGstDao.findByApplicationId(lendingApplicationOptional.get().getId());
            LendingMerchantPermissions lendingMerchantPermissions = lendingMerchantPermissionsDao.findByMerchantId(lendingApplicationOptional.get().getMerchantId());
            Boolean locationPermissionActive = "v2".equalsIgnoreCase(loanDashboardApiVersion.getApiVersion()) ? Boolean.TRUE : lendingMerchantPermissions.getLocationPermissionActive();
            List<LendingMerchantReferences> lendingMerchantReferences = lendingMerchantReferencesDao.findByMerchantIdAndApplicationId(lendingApplicationOptional.get().getMerchantId(), lendingApplicationOptional.get().getId());
            LendingMerchantDetails lendingMerchantDetails = lendingMerchantDetailsDao.findTop1ByMerchantIdOrderByIdDesc(lendingApplicationOptional.get().getMerchantId());
            RegulatoryApiRequestDto regulatoryApiRequestDto = RegulatoryApiRequestDto.builder()
                    .applicationId(lendingApplicationOptional.get().getId())
                    .lender(lendingApplicationOptional.get().getLender())
                    .productName("LENDING")
                    .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplicationOptional.get().getLoanType()))
                    .payload(RegulatoryApiRequestDto.Payload.builder()
                            .accountId(lendingApplicationOptional.get().getExternalLoanId())
                            .auditData(ConverterUtils.convertToBase64String(objectMapper.writeValueAsString(
                                    RegulatoryDataDto.builder()
                                            .address(merchantDetailsDto.getAddressDetail().get(0).getAddress())
                                            .businessCategory(lendingMerchantDetails.getBusinessCategory())
                                            .businessSubCategory(lendingMerchantDetails.getBusinessSubCategory())
                                            .customerConsent(locationPermissionActive)
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
                                            .consents(getConsents(lendingApplicationOptional.get()))
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

    private List<RegulatoryDataDto.Consent> getConsents(LendingApplication lendingApplication) {
        LendingApplicationKycDetails lendingApplicationKycDetails = lendingApplicationKycDetailsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(),lendingApplication.getLender());
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusOrderByIdDesc(lendingApplication.getId(),Status.ACTIVE.name());
        List<RegulatoryDataDto.Consent> consents = new ArrayList<>();
        consents.add(RegulatoryDataDto.Consent.builder()
                .type("Bureau consent")
                .content("I authorize Resilient Digi Services Private Limited (acting as an authorized agent) (“BharatPe Money”) and its Lending Partners to collect, store and verify the Credit Information / Credit Report from the Credit Information Company for processing my loan application")
                .ip(lendingApplication.getIp())
                .timestamp(String.valueOf(lendingApplicationLenderDetails.getCreatedAt().getTime()))
                .build()
        );
        consents.add(RegulatoryDataDto.Consent.builder()
                .type("MFI consent")
                .content("I declare and certify that my  annual household income is more than Rs 3,00,000 per annum")
                .ip(lendingApplication.getIp())
                .timestamp(String.valueOf(lendingApplicationLenderDetails.getCreatedAt().getTime()))
                .build()
        );
        consents.add(RegulatoryDataDto.Consent.builder()
                .type("Tnc & privacy policy consent")
                .content("I agree to BharatPe Money T&C, Privacy Policy, Consent T&C and the respective Lending Partner’s T&C and Privacy Policy")
                .ip(lendingApplication.getIp())
                .timestamp(String.valueOf(lendingApplicationLenderDetails.getCreatedAt().getTime()))
                .build()
        );
        consents.add(RegulatoryDataDto.Consent.builder()
                .type("Political and Residence consent")
                .content("I confirm that I am resident of Indian and I am not politically exposed person")
                .ip(lendingApplication.getIp())
                .timestamp(String.valueOf(lendingApplicationLenderDetails.getCreatedAt().getTime()))
                .build()
        );
        consents.add(RegulatoryDataDto.Consent.builder()
                .type("KYC and penny drop consent")
                .content("I authorize BharatPe Money and/or its Lending Partner to perform KYC checks (from C-KYC/UIDAI/NSDL/Digilocker or any other modes) for processing my loan application. I further authorize the above to conduct my bank account verification through penny drop/reverse penny drop or any other modes available.")
                .ip(lendingApplication.getIp())
                .timestamp(String.valueOf(lendingApplicationKycDetails.getAadharApprovedAt().getTime()))
                .build()
        );
        consents.add(RegulatoryDataDto.Consent.builder()
                .type("Contact consent")
                .content("(In the form of a consent)\n" +
                        "You confirm that you have taken an explicit \n" +
                        "consent from your references and agree that \n" +
                        "Resilient Digi Services Private Limited (RDSPL) may get in touch with your references in case you are unreachable. By providing/selecting your references below, RDSPL shall deem that you have obtained consent from such person after disclosing the purpose for which their reference is provided. Your references will help us get back in touch with you in case you are \n" +
                        "unreachable.")
                .ip(lendingApplication.getIp())
                .timestamp(String.valueOf(lendingApplication.getAgreementAt().getTime()))
                .build()
        );
        consents.add(RegulatoryDataDto.Consent.builder()
                .type("Agreement consent")
                .content("By clicking on “I Agree”, I accept the Key Fact Statement, \n" +
                        "Sanction and loan agreement, Terms & Conditions and \n" +
                        "Privacy Policy of LSP and Privacy Policy and Terms & \n" +
                        "Conditions of Aditya Birla Finance Limited.")
                .ip(lendingApplication.getIp())
                .timestamp(String.valueOf(lendingApplication.getAgreementAt().getTime()))
                .build()
        );
        return consents;
    }

    private String getFailedDocMapping(String docType) {
        if ("KFS_SANCTION_AGREEMENT".equalsIgnoreCase(docType))
            return "KFS_SANCTION_AGREEMENT";
        return docType;
    }

    public void uploadDocuments(Long applicationId, List<String> docs, boolean systemMangedState) {
        StringBuilder failedDocs = new StringBuilder("");
        StringBuilder currentDocumentStatus = new StringBuilder(LenderAssociationStatus.DOC_UPLOAD_COMPLETE.name());
        List<DocUploadPayload> docUploadPayloadList = createPayload(applicationId,docs, failedDocs, currentDocumentStatus, systemMangedState);
        if (ObjectUtils.isEmpty(docUploadPayloadList)){
            log.info("no data found for {}", applicationId);
            log.info("app {} status {}  failed {}", applicationId,LenderAssociationStatus.DOC_UPLOAD_FAILED.name(), failedDocs + docs.stream().collect(Collectors.joining(";")));
            updateLenderDetailsRecord(applicationId,LenderAssociationStatus.DOC_UPLOAD_FAILED.name(), null, failedDocs + docs.stream().collect(Collectors.joining(";")), null);
            return;
        }
        log.info("app {} status {}  failed {}", applicationId,currentDocumentStatus, failedDocs);

        INbfcLenderGateway apiGatewayV3 = lenderGatewayFactory.getLenderApiGateway(Lender.ABFL.name());
        for (DocUploadPayload docUploadPayload: docUploadPayloadList) {
            try {
                log.info("{} {} found for {}",docUploadPayload.getDocType(), docUploadPayload.getDocUploadApiRequestDto(), applicationId);
                if (ObjectUtils.isEmpty(docUploadPayload.getDocUploadApiRequestDto().getPayload().getFileUpload())) {
                    log.info("payload construct incomplete for {} {}", docUploadPayload.getDocType(), applicationId);
                    failedDocs.append(docUploadPayload.getDocType() + ";");
                    currentDocumentStatus.delete(0,currentDocumentStatus.length());
                    currentDocumentStatus.append(LenderAssociationStatus.DOC_UPLOAD_FAILED.name());
                    continue;
                }
                Pair<String, String> resp = uploadDoc(docUploadPayload.getDocUploadApiRequestDto(),apiGatewayV3,docUploadPayload.getDocType());
                if (ObjectUtils.isEmpty(resp) || LenderAssociationStatus.DOC_UPLOAD_FAILED.name().equalsIgnoreCase(resp.getLeft())) {
                    failedDocs.append(resp.getRight() + ";");
                    currentDocumentStatus.delete(0,currentDocumentStatus.length());
                    currentDocumentStatus.append(LenderAssociationStatus.DOC_UPLOAD_FAILED.name());
                }
            } catch (Exception e) {
                log.error("exception occurred while invoking doc upload {} {} {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
                failedDocs.append(docUploadPayload.getDocType() + ";");
                currentDocumentStatus.delete(0,currentDocumentStatus.length());
                currentDocumentStatus.append(LenderAssociationStatus.DOC_UPLOAD_FAILED.name());
            }
        }
        log.info("app {} status {}  failed {}", applicationId,currentDocumentStatus, failedDocs);
        updateLenderDetailsRecord(applicationId,currentDocumentStatus.toString(), null, failedDocs.toString(), null);
    }

    public Pair<String, String> uploadDoc(DocUploadApiRequestDto docUploadApiRequestDto, INbfcLenderGateway apiGatewayV3, String docType) {
        try {
            DocUploadApiResponse docUploadApiResponse = apiGatewayV3.invokeDocUpload(docUploadApiRequestDto);
            if (ObjectUtils.isEmpty(docUploadApiResponse) || ObjectUtils.isEmpty(docUploadApiResponse.getData()) ||  !StatusCheckResponse.SUCCESS.name().equalsIgnoreCase(docUploadApiResponse.getData().getResponseStatus())) {
                return Pair.of(LenderAssociationStatus.DOC_UPLOAD_FAILED.name(),docType);
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload event for app {} {} {}", docUploadApiRequestDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
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
            lendingApplicationLenderDetails.setFailedUpload( null == failedUploadDocs ? lendingApplicationLenderDetails.getFailedUpload(): failedUploadDocs);
            lendingApplicationLenderDetails.setDigitalDataUploadStatus(ObjectUtils.isEmpty(digitalDataUploadStatus)? lendingApplicationLenderDetails.getDigitalDataUploadStatus(): digitalDataUploadStatus);
            lendingApplicationLenderDetailsDao.save(lendingApplicationLenderDetails);
        } catch (Exception e) {
            log.error("exception occurred while saving data to table {} {} {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    public  List<DocUploadPayload> createPayload(Long applicationId, List<String> docTypes, StringBuilder failedDocs, StringBuilder currentDocumentStatus, boolean systemMangedState) {
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
        if (systemMangedState) {
            if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails.getFailedUpload())) {
                failedDocs.append(lendingApplicationLenderDetails.getFailedUpload());
            }
            if (!ObjectUtils.isEmpty(lendingApplicationLenderDetails.getDocUploadStatus())) {
                currentDocumentStatus.delete(0,currentDocumentStatus.length());
                currentDocumentStatus.append(lendingApplicationLenderDetails.getDocUploadStatus());
            }
        }
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
                        .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplicationOptional.get().getLoanType()))
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
                String docName = null;
                if ("KFS".equalsIgnoreCase(docType) || "KFS_SANCTION_AGREEMENT".equalsIgnoreCase(docType) || "SANCTION_AGREEMENT".equalsIgnoreCase(docType) || "WELCOME_LETTER".equalsIgnoreCase(docType)) {
                    if (ObjectUtils.isEmpty(lendingKfs)) {
                        log.info("kfs not found for {}", applicationId);
                        continue;
                    }
                    payload.setFileName(docType + "_" + lendingApplication.getId() + ".pdf");

                    if ("KFS_SANCTION_AGREEMENT".equalsIgnoreCase(docType)) {

                        String docKfsName = Optional.ofNullable(lendingKfs.getKfsDocFile()).orElse(KfsConstants.KFS_S3_KEY_PREFIX + lendingApplication.getId());
                        if (!s3BucketHandler.doesS3ObjectExist(bucket, docKfsName)) {
                            Optional<BasicDetailsDto> merchant = merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId());
                            lendingApplicationServiceV2.generateKfsDocument(lendingApplication, merchant.get(), lendingKfs, lendingKfs.getKfsSignedAt());
                            lendingKfs = lendingKfsDao.save(lendingKfs);
                        }

                        boolean generateLenderDocEnabled = "TOPUP".equalsIgnoreCase(lendingApplication.getLoanType()) ?
                                lenderDocGenerateTopUpEnabledLenders.contains(lendingApplication.getLender()) : lenderDocGenerateEnabledLenders.contains(lendingApplication.getLender());
                        if (generateLenderDocEnabled) {
                            String lenderDocRolloutDateString = LoanType.TOPUP.name().equalsIgnoreCase(lendingApplication.getLoanType()) ? lenderTopupDocRolloutDateTime : lenderDocRolloutDateTime;
                            Date lenderDocRolloutDate = DateTimeUtil.parseDate(lenderDocRolloutDateString, "yyyy-MM-dd hh:mm:ss");
                            if (lendingApplication.getAgreementAt().after(lenderDocRolloutDate)) {
                                log.info("skipping merging of docs for application {}, lenderDocRolloutDateTime: {}", lendingApplication, lenderDocRolloutDate);
                                payload.setFileUpload(ConverterUtils.convertPreSignedUrlToBase64String(s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(docKfsName, bucket)));
                                docUploadPayloadList.add(DocUploadPayload.builder().docType(docType).docUploadApiRequestDto(docUploadApiRequestDto).build());
                                log.info("payload size {} {}", docUploadPayloadList.size(), applicationId);
                                continue;
                            }
                        }

                        String docSanctionName = Optional.ofNullable(lendingKfs.getSanctionLoanAgreementDocFile()).orElse(KfsConstants.SANCTION_LOAN_AGREEMENT_S3_KEY_PREFIX + lendingApplication.getId());
                        if (!s3BucketHandler.doesS3ObjectExist( bucket, docSanctionName)) {
                            Optional<BasicDetailsDto> merchant = merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId());
                            lendingApplicationServiceV2.generateSanctionCumLoanAgreementDoc(lendingApplication, merchant.get(), lendingKfs, lendingKfs.getSanctionLoanAgreementSignedAt());
                            lendingKfs = lendingKfsDao.save(lendingKfs);
                        }

                        this.processKfsSanctionDocument(docType, payload, lendingApplication, docKfsName, docSanctionName);
                        docUploadApiRequestDto.setPayload(payload);

                    } else if ("WELCOME_LETTER".equalsIgnoreCase(docType)) {
                        docName = Optional.ofNullable(lendingKfs.getWelcomeDocFile()).orElse(KfsConstants.WELCOME_S3_KEY_PREFIX+ lendingApplication.getId() + ".pdf");
                        if (!s3BucketHandler.doesS3ObjectExist(bucket, docName) && !ObjectUtils.isEmpty(lendingApplication.getDisburseTimestamp())) {
                            Optional<BasicDetailsDto> merchant = merchantService.fetchMerchantBasicDetails(lendingApplication.getMerchantId());
                            lendingApplicationServiceV2.generateWelcomeDocument(lendingApplication,lendingKfs,merchant.get(), lendingApplication.getDisburseTimestamp());
                            lendingKfs = lendingKfsDao.save(lendingKfs);
                        }
                        payload.setFileUpload(ConverterUtils.convertPreSignedUrlToBase64String(s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(docName,bucket)));
                        docUploadApiRequestDto.setPayload(payload);
                    }
                } else if ("SHOP-FRONT".equalsIgnoreCase(docType) || "SHOP-STOCK".equalsIgnoreCase(docType)) {
                    LendingShopDocuments lendingShopDocument = lendingShopDocumentsDao.findTop1ByMerchantIdAndApplicationIdAndProofTypeOrderByIdDesc(lendingApplication.getMerchantId(), lendingApplication.getId(), docType);
                    log.info("lending shop doc {} {}",docType, lendingShopDocument);
                    if (ObjectUtils.isEmpty(lendingShopDocument) || ObjectUtils.isEmpty(lendingShopDocument.getProofFrontSide())) {
                        log.info("shop doc not found for {} {}", docType, applicationId);
                        continue;
                    }
                    docName = lendingShopDocument.getProofFrontSide();
                    payload.setFileName(docType + "_" + lendingApplication.getId() + ".jpeg");
                    payload.setFileUpload(ConverterUtils.convertPreSignedUrlToBase64String(s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(docName,bucket)));
                    docUploadApiRequestDto.setPayload(payload);
                }

                docUploadPayloadList.add(DocUploadPayload.builder().docType(docType).docUploadApiRequestDto(docUploadApiRequestDto).build());
                log.info("payload size {} {}", docUploadPayloadList.size(), applicationId);
            } catch (Exception e) {
                log.error("some issue occurred while creating doc payload {} {}", e.getMessage(), Arrays.asList(e.getStackTrace()));
            }
        }
        return docUploadPayloadList;
    }

    /**
     * processKfsSanctionDocument
     * @param docType docType
     * @param payload payload
     * @param lendingApplication lendingApplication
     * @param docKfsName docKfsName
     * @param docSanctionName docSanctionName
     * @throws IOException IOException
     * @throws DocumentException DocumentException
     */
    private void processKfsSanctionDocument(String docType,
                                            DocUploadApiRequestDto.Payload payload,
                                            LendingApplication lendingApplication,
                                            String docKfsName, String docSanctionName) throws IOException, DocumentException {
        /*
            1. download file from bucket to local storage
            2. merge both file in new merged file using ipdf
            3. set base64 payload to payload object
            4. delete file from local storage
         */

        String mergedFileName = "KFS_SANCTION_AGREEMENT_MERGED_"+ lendingApplication.getId() + ".pdf";


        // Download the first PDF file
        URL url1 = new URL(s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(docKfsName,bucket));
        URLConnection connection1 = url1.openConnection();
        InputStream inputStream1 = connection1.getInputStream();
        PdfReader reader1 = new PdfReader(inputStream1);

        // Download the second PDF file
        URL url2 = new URL(s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(docSanctionName,bucket));
        URLConnection connection2 = url2.openConnection();
        InputStream inputStream2 = connection2.getInputStream();
        PdfReader reader2 = new PdfReader(inputStream2);

        // Create the output file
        Document document = new Document();
        PdfCopy copy = new PdfCopy(document, Files.newOutputStream(Paths.get("/data/" + mergedFileName)));
        copy.setCompressionLevel(9);
        document.open();

        // Merge the PDF files
        copy.addDocument(reader1);
        copy.addDocument(reader2);

        // Close the document
        document.close();

        File mergedFile = new File("/data/" + mergedFileName);
        s3BucketHandler.uploadFileToS3(mergedFile,"loan-document",mergedFileName);
        String mergeDocumentPresignedUrl = s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(mergedFileName,bucket);

        log.info("pre-signed url for merged doc: {}, {}", lendingApplication.getId(),  mergeDocumentPresignedUrl);

        payload.setFileName(docType + "_" + lendingApplication.getId() + ".pdf");
        payload.setFileUpload(ConverterUtils.convertPreSignedUrlToBase64String(mergeDocumentPresignedUrl));

        Path uploadedFilePath = Paths.get(CURRENT_DIR + "/" + mergedFileName);
        FileUtil.deleteFile(uploadedFilePath);

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
            String gst = ObjectUtils.isEmpty(lendingGstDetail.getGstNumber()) ? null : lendingGstDetail.getGstNumber();
            DigitalDataUploadRequest digitalDataUploadRequest = DigitalDataUploadRequest.builder()
                    .applicationId(lendingApplicationOptional.get().getId())
                    .lender(lendingApplicationOptional.get().getLender())
                    .productName("LENDING")
                    .topup(LoanType.TOPUP.name().equalsIgnoreCase(lendingApplicationOptional.get().getLoanType()))
                    .payload(DigitalDataUploadRequest.Payload.builder()
                            .category(converterUtils.parseDataExtended(lendingMerchantDetails.getBusinessCategory()))
                            .companyCategory(lendingMerchantDetails.getBusinessSubCategory())
                            .cabTransactionData(gst)
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
    public void pushDataToNbfc(Long applicationId, List<String> documents, boolean systemMangedState, boolean digiSignRetry) {
        if (systemMangedState) {
            try {
                log.info("invoking regulatory for {}", applicationId);
                uploadRegulatoryData(applicationId);
            } catch (Exception e) {
                log.error("error occurred while uploading regulatory data {} {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()) );
            }
            try {
                log.info("invoking digital for {}", applicationId);
                uploadDigitalData(applicationId);
            } catch (Exception e) {
                log.error("error occurred while uploading digital data {} {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()) );
            }
        }
        try {
            if(!ObjectUtils.isEmpty(documents) && documents.get(0).equalsIgnoreCase("skip_docs")){
                log.info("skipping doc upload as pushDataToNbfc called via digi sign");
            } else {
                log.info("invoking docs for {}", applicationId);
                uploadDocuments(applicationId, documents, systemMangedState);
            }
        } catch (Exception e) {
            log.error("error occurred while uploading docs data {} {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()) );
        }
        if(digiSignRetry){
            try {
                log.info("invoking digiSign for {}", applicationId);
                abflDigiSignService.invoke(applicationId, new HashMap<>());
            } catch (Exception e) {
                log.error("error occurred while uploading digisign docs data {} {}",applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()) );
            }
        }
    }
}