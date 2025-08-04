package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingResubmitTaskDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingResubmitTask;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.enums.LoanType;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV3.config.TrillionLoansConfig;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLDocUploadRequestDto;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.validations.TLPayloadValidation;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.bharatpe.lending.loanV3.utils.NbfcUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TLDocUploadService {
    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonService commonService;

    @Autowired
    TLPayloadValidation payloadValidation;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    LendingResubmitTaskDao lendingResubmitTaskDao;

    @Lazy
    @Autowired
    NbfcUtils nbfcUtils;

    @Value("${aws.s3.bucket:loan-document}")
    private String bucket;

    @Autowired
    TrillionLoansConfig trillionLoansConfig;

    @Transactional
    public boolean invokeDocUpload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, String docType) {
        DocType docName = null;
        boolean isTopup = false;
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId())) {
                log.info("pre-requisite data not found {}", lenderAssociationDetailsDto.getApplicationId());
                return false;
            }
            docName = getDocName(docType);
            isTopup = LoanType.TOPUP.name().equalsIgnoreCase(lenderAssociationDetailsDto.getLendingApplication().getLoanType());

            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(docType);
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "PENDING", isTopup).name());
            lenderAssociationDetailsDto.setModifyLender(Boolean.TRUE);
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            boolean isEligibleForLenderKyc = kycUtils.isELigibleForLenderKyc(Lender.TRILLIONLOANS.name(), lenderAssociationDetailsDto.getLendingApplication().getMerchantId(), isTopup);
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }

            if (payloadValidation.isInValidDocUploadPayload(lenderAssociationDetailsDto.getCKycResponseDto(), isEligibleForLenderKyc)) {
                log.info("invalid response from downstream api for TrillionLoans docUpload: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "FAILED", isTopup).name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName, "FAILED", isTopup));
                return false;
            }
            NBFCRequestDTO<?> documentUploadRequest = getPayload(lenderAssociationDetailsDto, docName, isTopup);
            if (Objects.isNull(documentUploadRequest)) {
                log.info("error in doc upload payload of TrillionLoans for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "FAILED", isTopup).name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName, "FAILED", isTopup));
                return false;
            }
            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(documentUploadRequest, LenderAssociationStages.valueOf(docType), trillionLoansConfig.getDocUploadTimeoutThreshold());
            log.info("docUpload response of TrillionLoans from nbfc for docTYpe: {} {} with applicationId: {}", nbfcResponseDto, docName, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto)) {
                if(nbfcResponseDto.getSuccess()) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "SUCCESS", isTopup).name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    if (isEligibleForLenderKyc) {
                        List<String> stagesToBeInvokedInOrder = Collections.singletonList(LenderAssociationStages.KYC.name());
                        Optional<String> failureStage = stagesToBeInvokedInOrder.stream().filter(stage -> !nbfcUtils.invokeSpecificStage(lenderAssociationDetailsDto.getLendingApplication().getLender(), lenderAssociationDetailsDto, stage)).findFirst();
                        if (failureStage.isPresent()) {
                            log.info("TL: lender association failed at {} stage for applicationId {}  with lender {}", failureStage.get(), lenderAssociationDetailsDto.getApplicationId(), lenderAssociationDetailsDto.getLendingApplication().getLender());
                            return false;
                        }
                    }
                    return true;
                }
                if(nbfcResponseDto.getRetry()) {
                    log.info("DocUpload request of trillionLoans for docType {} pushed to retry for {}", docType, lenderAssociationDetailsDto.getApplicationId());
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "RETRY", isTopup).name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload of TrillionLoans for {} {} {} {}", docType, lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "FAILED", isTopup).name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName, "FAILED", isTopup));
        return false;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto, DocType docName, boolean isTopup) {
        try {
            TLDocUploadRequestDto docUploadRequest = TLDocUploadRequestDto.builder()
                    .clientId(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getCccId())
                    .leadId(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())
                    .name(getDocumentName(docName))
                    .metaData(TLDocUploadRequestDto.MetaData.builder()
                            .docType(docName.getContentType())
                            .fileName(docName.name() + docName.getFileExtension())
                            .url("URL".equalsIgnoreCase(docName.getContentType()) ? getFileBlob(docName, lenderAssociationDetailsRequestDto.getCKycResponseDto(), null) : null)
                            .data("RAW_DATA".equalsIgnoreCase(docName.getContentType()) ? getFileBlob(docName, lenderAssociationDetailsRequestDto.getCKycResponseDto(), null) : null)
                            .score(!ObjectUtils.isEmpty(lenderAssociationDetailsRequestDto.getCKycResponseDto().getSelfieLivelinessScore()) ? String.format("%.2f", (lenderAssociationDetailsRequestDto.getCKycResponseDto().getSelfieLivelinessScore() * 100)) : "0.0")
                            .build())
                    .build();

            return NBFCRequestDTO.builder()
                    .applicationId(lenderAssociationDetailsRequestDto.getApplicationId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.TRILLIONLOANS.name())
                    .payload(docUploadRequest)
                    .topup(isTopup)
                    .build();
        } catch (Exception e) {
            log.info("Exception while creating docUpload payload of TrillionLoans for applicationId: {}, {}", lenderAssociationDetailsRequestDto.getLendingApplication(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    // mapper for docName at TrillionLoans side
    private String getDocumentName(DocType docType) {
        switch (docType.name()) {
            case "DIGILOCKER_AADHAAR_XML":
                return "aadhaar_xml";
            case "SELFIE":
                return "selfie";
            case "LOAN_AGREEMENT":
                return "loan_agreement";
            default:
                return null;
        }
    }

    private DocType getDocName(String docType) {
        switch (docType) {
            case "AADHAR_UPLOAD":
                return DocType.DIGILOCKER_AADHAAR_XML;
            case "SELFIE_UPLOAD":
                return DocType.SELFIE;
            default:
                return null;
        }
    }

    private LenderAssociationStatus getStatusforDocumentUpload(DocType docType, String currentStage, boolean isTopup) {
        switch (docType.name()) {
            case "DIGILOCKER_AADHAAR_XML":
                switch (currentStage) {
                    case "FAILED":
                        return LenderAssociationStatus.AADHAR_UPLOAD_FAILED;
                    case "SUCCESS":
                        return isTopup ? LenderAssociationStatus.AADHAR_UPLOAD_IN_PROGRESS : LenderAssociationStatus.AADHAR_UPLOAD_SUCCESS;
                    case "RETRY" :
                        return LenderAssociationStatus.AADHAR_UPLOAD_RETRY;
                    default:
                        return LenderAssociationStatus.AADHAR_UPLOAD_PENDING;
                }
            case "SELFIE":
                switch (currentStage) {
                    case "FAILED":
                        return LenderAssociationStatus.SELFIE_UPLOAD_FAILED;
                    case "SUCCESS":
                        return LenderAssociationStatus.SELFIE_UPLOAD_SUCCESS;
                    case "RETRY" :
                        return LenderAssociationStatus.SELFIE_UPLOAD_RETRY;
                    default:
                        return LenderAssociationStatus.SELFIE_UPLOAD_PENDING;
                }
            default:
                return null;
        }
    }

    private String getFileBlob(DocType fileBlob, CKycResponseDto cKycResponseDto, LendingKfs lendingKfs) {
        String key = null;
        switch (fileBlob.name()) {
            case "DIGILOCKER_AADHAAR_XML":
                return cKycResponseDto.getPoaString();
            case "SELFIE":
                return cKycResponseDto.getSelfieString();
            case "KEY_FACT_STATEMENT":
            case "KEY_FACT_STATEMENT_NEW":
                key = lendingKfs.getKfsDocFile();
                break;
            case "LOAN_AGREEMENT":
            case "LOAN_AGREEMENT_NEW":
                key = lendingKfs.getSanctionLoanAgreementDocFile();
                break;
            default:
                return null;
        }
        return getS3PresignedUrlFromKey(key);
    }

    private String getS3PresignedUrlFromKey(String key) {
        log.info("key to fetch from aws: {}", key);
        return ObjectUtils.isEmpty(key) ? "" : s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(key, bucket);
    }

    @Transactional
    public boolean invokeAdditionalDocUpload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String docType) {
        try {
            NBFCRequestDTO documentUploadRequest = getAdditionalDocPayload(lendingApplication.getId(), lendingApplicationLenderDetails, DocType.valueOf(docType));
            if (Objects.isNull(documentUploadRequest) || Objects.isNull(documentUploadRequest.getPayload())) {
                log.info("error in doc upload payload of TrillionLoans for applicationId: {}", lendingApplication.getId());
                return false;
            }
            log.info("request body {}", new ObjectMapper().writeValueAsString(documentUploadRequest));
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(documentUploadRequest, LenderAssociationStages.DOC_UPLOAD);
            log.info("docUpload response of TrillionLoans from nbfc for docTYpe: {} {} with applicationId: {}", nbfcResponseDto, docType, lendingApplication.getId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess()) {
                return true;
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload of TrillionLoans for {} {} {} {}", docType, lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private NBFCRequestDTO getAdditionalDocPayload(Long applicationId, LendingApplicationLenderDetails lendingApplicationLenderDetails, DocType docName) {
        try {
            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, lendingApplicationLenderDetails.getLender());
            if (ObjectUtils.isEmpty(lendingKfs)) {
                throw new RuntimeException("Unable to fetch lending kfs and loan agreement documents for application " + applicationId);
            }

            LendingResubmitTask lendingResubmitTask = lendingResubmitTaskDao.findTopByApplicationId(applicationId);
            List<DocType> documentList = (lendingResubmitTask != null && lendingResubmitTask.getDowngrade() != null && lendingResubmitTask.getDowngrade() && lendingResubmitTask.getDowngradeDone() != null && !lendingResubmitTask.getDowngradeDone()) ?
                    Arrays.asList(DocType.KEY_FACT_STATEMENT_NEW, DocType.LOAN_AGREEMENT_NEW) :
                    Arrays.asList(DocType.KEY_FACT_STATEMENT, DocType.LOAN_AGREEMENT);

            TLDocUploadRequestDto docUploadRequest = TLDocUploadRequestDto.builder()
                    .name(getDocumentName(docName))
                    .clientId(lendingApplicationLenderDetails.getCccId())
                    .leadId(lendingApplicationLenderDetails.getLeadId())
                    .metaDataList(documentList.stream()
                            .map(docType -> TLDocUploadRequestDto.MetaData.builder()
                                    .docType("URL")
                                    .docName(docType.name())
                                    .fileName(docType.name() + docType.getFileExtension())
                                    .url(getFileBlob(docType, null, lendingKfs))
                                    .build())
                            .collect(Collectors.toList()))
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(applicationId)
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.TRILLIONLOANS.name())
                    .payload(docUploadRequest)
                    .build();
        } catch (Exception e) {
            log.info("Exception while creating docUpload payload of TrillionLoans for applicationId: {}, {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
