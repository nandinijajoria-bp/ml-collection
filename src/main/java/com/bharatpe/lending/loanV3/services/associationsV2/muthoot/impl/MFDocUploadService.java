package com.bharatpe.lending.loanV3.services.associationsV2.muthoot.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFDocUploadRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFDocUploadCallbackResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.muthoot.MFDocUploadResponseDTO;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.DocumentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class MFDocUploadService {
    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${aws.s3.bucket:loan-document}")
    private String bucket;

    @Autowired
    DocUploadUtils docUploadUtils;

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
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "PENDING").name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getLendingApplication().getMerchantId()));
            }
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                log.info("invalid response from downstream api for Muthoot docUpload: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "FAILED").name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName, "FAILED"));
                return false;
            }
            NBFCRequestDTO documentUploadRequest = getPayload(lenderAssociationDetailsDto, docName);
            if (Objects.isNull(documentUploadRequest)) {
                log.info("error in doc upload payload of Muthoot for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "FAILED").name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName, "FAILED"));
                return false;
            }
            NBFCResponseDTO nbfcResponseDTO = lenderAPIGateway.invokeStage(documentUploadRequest, LenderAssociationStages.valueOf(docType));
            log.info("docUpload response of Muthoot from nbfc for docTYpe: {} {} with applicationId: {}", nbfcResponseDTO, docName, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {
                MFDocUploadResponseDTO uploadResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), MFDocUploadResponseDTO.class);
                if ("DOC-S-000".equalsIgnoreCase(uploadResponseDTO.getStatusCode())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "SUCCESS").name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload of Muthoot for {} {} {} {}", docType, lenderAssociationDetailsDto.getApplicationId(), e.getMessage(),
                    Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "FAILED").name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName, "FAILED"));
        return false;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto, DocType docName) {
        try {
            MFDocUploadRequestDTO docUploadRequest = MFDocUploadRequestDTO.builder()
                    .program("EDI")
                    .customerId(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())
                    .documentType(getDocumentId(docName))
                    .metaData(MFDocUploadRequestDTO.MetaData.builder()
                            .docType(docName.getContentType())
                            .fileName(docName.name() + docName.getFileExtension())
                            .url("URL".equalsIgnoreCase(docName.getContentType()) ? getFileBlob(lenderAssociationDetailsRequestDto.getApplicationId(), docName, lenderAssociationDetailsRequestDto.getCKycResponseDto(), null) : null)
                            .data("RAW_DATA".equalsIgnoreCase(docName.getContentType()) ? getFileBlob(lenderAssociationDetailsRequestDto.getApplicationId(), docName, lenderAssociationDetailsRequestDto.getCKycResponseDto(), null) : null)
                            .build())
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(lenderAssociationDetailsRequestDto.getApplicationId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.MUTHOOT.name())
                    .payload(docUploadRequest)
                    .build();
        } catch (Exception e) {
            log.error("Exception while creating docUpload payload of Muthoot for applicationId: {}, {}, {}", lenderAssociationDetailsRequestDto.getLendingApplication(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getDocumentId(DocType docType) {
        switch (docType.name()) {
            case "SELFIE":
                return "SELFIE";
            case "KEY_FACT_STATEMENT_LOAN_AGREEMENT_MERGED":
                return "LOAN_AGREEMENT";
            case "DIGILOCKER_AADHAAR_XML":
                return "AADHAAR_XML";
            default:
                return null;
        }
    }

    private DocType getDocName(String docType) {
        switch (docType) {
            case "SELFIE_UPLOAD":
                return DocType.SELFIE;
            case "AADHAR_UPLOAD":
                return DocType.DIGILOCKER_AADHAAR_XML;
            default:
                return null;
        }
    }

    private LenderAssociationStatus getStatusforDocumentUpload(DocType docType, String currentStage) {
        switch (docType.name()) {
            case "SELFIE":
                switch (currentStage) {
                    case "FAILED":
                        return LenderAssociationStatus.SELFIE_UPLOAD_FAILED;
                    case "SUCCESS":
                        return LenderAssociationStatus.SELFIE_UPLOAD_IN_PROGRESS;
                    default:
                        return LenderAssociationStatus.SELFIE_UPLOAD_PENDING;
                }
            case "DIGILOCKER_AADHAAR_XML":
                switch (currentStage) {
                    case "FAILED":
                        return LenderAssociationStatus.AADHAR_UPLOAD_FAILED;
                    case "SUCCESS":
                        return LenderAssociationStatus.AADHAR_UPLOAD_SUCCESS;
                    default:
                        return LenderAssociationStatus.AADHAR_UPLOAD_PENDING;
                }
            default:
                return null;
        }
    }

    private String getFileBlob(Long applicationId, DocType fileBlob, CKycResponseDto cKycResponseDto, LendingKfs lendingKfs)
            throws DocumentException, IOException {
        String key = null;
        switch (fileBlob.name()) {
            case "SELFIE":
                return cKycResponseDto.getSelfieString();
            case "DIGILOCKER_AADHAAR_XML":
                return cKycResponseDto.getPoaString();
            case "KEY_FACT_STATEMENT_LOAN_AGREEMENT_MERGED":
                String kfsDocFile = lendingKfs.getKfsDocFile();
                String loanAgreementDocFile = lendingKfs.getSanctionLoanAgreementDocFile();
                return docUploadUtils.mergeUnsignedDocs(applicationId, kfsDocFile, loanAgreementDocFile);
            case "KEY_FACT_STATEMENT":
                key = lendingKfs.getKfsDocFile();
                break;
            case "LOAN_AGREEMENT":
                key = lendingKfs.getSanctionLoanAgreementDocFile();
                break;
            default:
                return null;
        }
        return docUploadUtils.getS3PresignedUrlFromKey(key);
    }

    public boolean invokeAdditionalDocUpload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String docType) {
        try {
            NBFCRequestDTO documentUploadRequest = getAdditionalDocPayload(lendingApplication.getId(), lendingApplicationLenderDetails, DocType.valueOf(docType));
            if (Objects.isNull(documentUploadRequest) || Objects.isNull(documentUploadRequest.getPayload())) {
                log.info("error in doc upload payload of MUTHOOT for applicationId: {}", lendingApplication.getId());
                return false;
            }
            log.info("request body {}", new ObjectMapper().writeValueAsString(documentUploadRequest));
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(documentUploadRequest, LenderAssociationStages.DOC_UPLOAD);
            log.info("docUpload response of MUTHOOT from nbfc for docTYpe: {} {} with applicationId: {}", nbfcResponseDto, docType, lendingApplication.getId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                MFDocUploadResponseDTO uploadResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), MFDocUploadResponseDTO.class);
                if ("DOC-S-000".equalsIgnoreCase(uploadResponseDTO.getStatusCode())) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload of MUTHOOT for {} {} {} {}", docType, lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private NBFCRequestDTO getAdditionalDocPayload(Long applicationId, LendingApplicationLenderDetails lendingApplicationLenderDetails, DocType docName) {
        try {
            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
            if (ObjectUtils.isEmpty(lendingKfs)) {
                throw new RuntimeException("Unable to fetch lending kfs and loan agreement documents for merchant");
            }
            MFDocUploadRequestDTO docUploadRequest = MFDocUploadRequestDTO.builder()
                    .program("EDI")
                    .documentType(getDocumentId(docName))
                    .customerId(lendingApplicationLenderDetails.getLeadId())
                    .metaData(MFDocUploadRequestDTO.MetaData.builder()
                            .docType(docName.getContentType())
                            .fileName(docName.name() + docName.getFileExtension())
                            .url("URL".equalsIgnoreCase(docName.getContentType()) ? getFileBlob(applicationId, docName, null, lendingKfs) : null)
                            .data("RAW_DATA".equalsIgnoreCase(docName.getContentType()) ? getFileBlob(applicationId, docName, null, lendingKfs) : null)
                            .build())
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(applicationId)
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.MUTHOOT.name())
                    .payload(docUploadRequest)
                    .build();
        } catch (Exception e) {
            log.error("Exception while creating docUpload payload of MUTHOOT for applicationId: {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }


    public Boolean processMFDocUploadCallback(NBFCResponseDTO nbfcResponseDTO) {
        try {

            LendingApplication lendingApplication = lendingApplicationDao.findById(Long.valueOf(nbfcResponseDTO.getApplicationId())).orElse(null);
            if (ObjectUtils.isEmpty(lendingApplication)) {
                log.info("No application found for applicationId : {}", nbfcResponseDTO.getApplicationId());
                return false;
            }
            LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lendingApplicationLenderDetails) || !nbfcResponseDTO.getLender().equalsIgnoreCase(lendingApplicationLenderDetails.getLender())) {
                log.info("No LendingApplicationLenderDetails found with lender {} for applicationId {}", lendingApplication.getLender(), lendingApplication.getId());
                return false;
            }
            if (!LenderAssociationStages.DOC_UPLOAD.name().equalsIgnoreCase(lendingApplicationLenderDetails.getStage())
                    || !LenderAssociationStatus.DOC_UPLOAD_IN_PROGRESS.name().equalsIgnoreCase(lendingApplicationLenderDetails.getDocUploadStatus())) {
                log.info("Application not in correct state for Doc Upload callback for applicationId {}", lendingApplication.getId());
                return false;
            }
            LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest = LenderAssociationDetailsRequestDto.builder()
                    .applicationId(lendingApplication.getId())
                    .lendingApplication(lendingApplication)
                    .lendingApplicationLenderDetails(lendingApplicationLenderDetails)
                    .manageState(true)
                    .modifyLender(false)
                    .build();
            if (Objects.nonNull(nbfcResponseDTO.getData()) && nbfcResponseDTO.getSuccess()) {
                MFDocUploadCallbackResponseDTO docUploadCallbackResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDTO.getData()), MFDocUploadCallbackResponseDTO.class);
                log.info("DocUpload callback Response of Muthoot for {} {}", nbfcResponseDTO.getApplicationId(), docUploadCallbackResponseDTO);
                if (!ObjectUtils.isEmpty(docUploadCallbackResponseDTO)) {
                    if ("COMPLETED".equalsIgnoreCase(docUploadCallbackResponseDTO.getData().getStatus()) || "SUCCESS".equalsIgnoreCase(docUploadCallbackResponseDTO.getData().getStatus())) {
                        docUploadUtils.saveESignedDocs(lendingApplication.getId(), docUploadCallbackResponseDTO.getData().getAgreementURL(), docUploadCallbackResponseDTO.getData().getAgreementURL());
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setESignedSanc(ObjectUtils.isEmpty(docUploadCallbackResponseDTO.getData().getAgreementURL()) ? null : true);
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setESignedKfs(ObjectUtils.isEmpty(docUploadCallbackResponseDTO.getData().getAgreementURL()) ? null : true);
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setDocUploadStatus(LenderAssociationStatus.DOC_UPLOAD_COMPLETE.name());
                        commonService.manageApplicationStateAndPushToNextStage(lenderAssociationDetailsRequest);
                        return true;
                    }
                    if ("REJECTED".equalsIgnoreCase(docUploadCallbackResponseDTO.getData().getStatus()) || "FAILED".equalsIgnoreCase(docUploadCallbackResponseDTO.getData().getStatus())) {
                        lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setDocUploadStatus(LenderAssociationStatus.DOC_UPLOAD_FAILED.name());
                        commonService.manageApplicationState(lenderAssociationDetailsRequest);
                        return false;
                    }
                }
            }
            lenderAssociationDetailsRequest.getLendingApplicationLenderDetails().setDocUploadStatus(LenderAssociationStatus.DOC_UPLOAD_FAILED.name());
            commonService.manageApplicationState(lenderAssociationDetailsRequest);
        } catch (Exception e) {
            log.error("exception while processing DocUpload callback of Muthoot for  {} {} {}", nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }


}
