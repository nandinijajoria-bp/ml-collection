package com.bharatpe.lending.loanV3.services.associationsV2.capri.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.capri.CapriDocUploadRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.capri.CapriDocUploadResponseDTO;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.capri.validations.CapriPayloadValidation;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class CapriDocUploadService {

    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonService commonService;

    @Autowired
    CapriPayloadValidation capriPayloadValidation;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${aws.s3.bucket:loan-document}")
    private String bucket;

    @Transactional
    public boolean invokeDocUpload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, String docType) {
        DocType docName = null;
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId())) {
                log.info("pre-requisite data not found {}", lenderAssociationDetailsDto.getApplicationId());
                return false;
            }
            docName = getDocName(docType);
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(docType);
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusForDocumentUpload(docName,"PENDING").name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }
            if (capriPayloadValidation.isInValidDocUploadPayload(lenderAssociationDetailsDto.getCKycResponseDto())) {
                log.info("invalid response from downstream api for Capri docUpload: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusForDocumentUpload(docName,"FAILED").name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusForDocumentUpload(docName,"FAILED"));
                return false;
            }
            NBFCRequestDTO documentUploadRequest = getPayload(lenderAssociationDetailsDto, docName);
            if (Objects.isNull(documentUploadRequest)) {
                log.info("error in doc upload payload of Capri for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusForDocumentUpload(docName,"FAILED").name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusForDocumentUpload(docName,"FAILED"));
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(documentUploadRequest, LenderAssociationStages.valueOf(docType));
            log .info("docUpload response of Capri from nbfc for docTYpe: {} {} with applicationId: {}", nbfcResponseDto, docName, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess()) {
                log.info("doc upload request of Capri success for {} {}", docName, lenderAssociationDetailsDto.getApplicationId());
                CapriDocUploadResponseDTO docUploadResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), CapriDocUploadResponseDTO.class);
                if("Aadhaar Xml".equalsIgnoreCase(docUploadResponseDTO.getSource()) && !"SUCCESS".equalsIgnoreCase(docUploadResponseDTO.getStatus())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusForDocumentUpload(docName,"FAILED").name());
                    commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusForDocumentUpload(docName,"FAILED"));
                }
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusForDocumentUpload(docName,"SUCCESS").name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload of Capri for {} {} {} {}", docType, lenderAssociationDetailsDto.getApplicationId(), e.getMessage(),
                    Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusForDocumentUpload(docName,"FAILED").name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusForDocumentUpload(docName,"FAILED"));
        return false;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto, DocType docName) {
        try {
            CapriDocUploadRequestDTO docUploadRequest = CapriDocUploadRequestDTO.builder()
                    .leadId(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())
                    .clientId(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getCccId())
                    .documentId(getDocumentName(docName))
                    .fileType(docName.getFileExtension())
                    .metaData(CapriDocUploadRequestDTO.MetaData.builder()
                            .docType(docName.getContentType())
                            .fileName(docName.name())
                            .url("URL".equalsIgnoreCase(docName.getContentType()) ? getFileBlob(docName, lenderAssociationDetailsRequestDto.getCKycResponseDto(), null) : null)
                            .data("RAW_DATA".equalsIgnoreCase(docName.getContentType()) ? getFileBlob(docName, lenderAssociationDetailsRequestDto.getCKycResponseDto(), null) : null)
                            .build())
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(lenderAssociationDetailsRequestDto.getApplicationId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.CAPRI.name())
                    .payload(docUploadRequest)
                    .build();
        } catch (Exception e) {
            log.info("Exception while creating docUpload payload of Capri for applicationId: {}, {}", lenderAssociationDetailsRequestDto.getLendingApplication(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    // mapper for docName at Capri side
    private String getDocumentName(DocType docType) {
        switch (docType.name()) {
            case "DIGILOCKER_AADHAAR_XML":
                return "aadhaar_xml";
            case "SELFIE":
                return "selfie";
            case "KEY_FACT_STATEMENT":
                return "key_fact_sheet";
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

    private LenderAssociationStatus getStatusForDocumentUpload(DocType docType, String currentStage) {
        switch (docType.name()) {
            case "DIGILOCKER_AADHAAR_XML":
                switch (currentStage) {
                    case "FAILED":
                        return LenderAssociationStatus.AADHAR_UPLOAD_FAILED;
                    case "SUCCESS":
                        return LenderAssociationStatus.AADHAR_UPLOAD_SUCCESS;
                    default:
                        return LenderAssociationStatus.AADHAR_UPLOAD_PENDING;
                }
            case "SELFIE":
                switch (currentStage) {
                    case "FAILED":
                        return LenderAssociationStatus.SELFIE_UPLOAD_FAILED;
                    case "SUCCESS":
                        return LenderAssociationStatus.SELFIE_UPLOAD_SUCCESS;
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
                key = lendingKfs.getKfsDocFile();
                break;
            case "LOAN_AGREEMENT":
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

            NBFCRequestDTO documentUploadRequest= getAdditionalDocPayload(lendingApplication.getId(), lendingApplicationLenderDetails, DocType.valueOf(docType));
            if (Objects.isNull(documentUploadRequest) || Objects.isNull(documentUploadRequest.getPayload())) {
                log.info("error in doc upload payload of Capri for applicationId: {}", lendingApplication.getId());
                return false;
            }
            log.info("request body {}", new ObjectMapper().writeValueAsString(documentUploadRequest));
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(documentUploadRequest, LenderAssociationStages.DOC_UPLOAD);
            log.info("docUpload response of Capri from nbfc for docTYpe: {} {} with applicationId: {}", nbfcResponseDto, docType, lendingApplication.getId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess()) {
                return true;
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload of Capri for {} {} {} {}", docType, lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private NBFCRequestDTO getAdditionalDocPayload(Long applicationId, LendingApplicationLenderDetails lendingApplicationLenderDetails, DocType docName) {
        try {
            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
            if(ObjectUtils.isEmpty(lendingKfs)) {
                throw new RuntimeException("Unable to fetch lending kfs and loan agreement documents for application " + applicationId);
            }
            CapriDocUploadRequestDTO docUploadRequest = CapriDocUploadRequestDTO.builder()
                    .documentId(getDocumentName(docName))
                    .leadId(lendingApplicationLenderDetails.getLeadId())
                    .clientId(lendingApplicationLenderDetails.getCccId())
                    .fileType("")
                    .metaData(CapriDocUploadRequestDTO.MetaData.builder()
                            .docType("URL")
                            .fileName(docName.name() + docName.getFileExtension())
                            .url(getFileBlob(docName, null,  lendingKfs))
                            .build())
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(applicationId)
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.CAPRI.name())
                    .payload(docUploadRequest)
                    .build();
        } catch (Exception e) {
            log.info("Exception while creating docUpload payload of Capri for applicationId: {}, {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

}
