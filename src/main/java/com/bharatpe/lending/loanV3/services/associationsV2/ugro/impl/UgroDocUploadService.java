package com.bharatpe.lending.loanV3.services.associationsV2.ugro.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroDocumentUploadRequest;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.ugro.validations.UgroPayloadValidation;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.itextpdf.text.DocumentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class UgroDocUploadService {
    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    DocUploadUtils docUploadUtils;

    @Autowired
    UgroPayloadValidation payloadValidation;

    @Transactional
    public boolean invokeDocUpload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, String docType) {
        DocType docName = null;
        try {
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getApplicationId())) {
                log.info("UGRO: pre-requisite data not found {}", lenderAssociationDetailsDto.getApplicationId());
                return false;
            }

            docName = getDocName(docType);
            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getLendingApplication().getMerchantId()));
            }
            if (payloadValidation.isInValidDocUploadPayload(lenderAssociationDetailsDto.getCKycResponseDto()) || ObjectUtils.isEmpty(docName)) {
                log.error("UGRO: CKyc/DocName not available for applicationId: {}, {}", lenderAssociationDetailsDto.getApplicationId(), docName);
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "FAILED").name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName, "FAILED"));
                return false;
            }

            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(docType);
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "PENDING").name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            NBFCRequestDTO<?> documentUploadRequest = getPayload(lenderAssociationDetailsDto, docName);
            if (ObjectUtils.isEmpty(documentUploadRequest) || ObjectUtils.isEmpty(documentUploadRequest.getPayload())) {
                log.info("UGRO: error in doc upload payload for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "FAILED").name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName, "FAILED"));
                return false;
            }
            NBFCResponseDTO<?> nbfcResponseDTO = lenderAPIGateway.invokeStage(documentUploadRequest, LenderAssociationStages.valueOf(docType));
            if (!ObjectUtils.isEmpty(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDTO.getData())) {
                log.info("UGRO: doc upload request success for {} {}", docName, lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "SUCCESS").name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
        } catch (Exception e) {
            log.error("UGRO: exception occurred while invoking doc upload for {} {} {} {}", docType, lenderAssociationDetailsDto.getApplicationId(), e.getMessage(),
                    Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "FAILED").name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName, "FAILED"));
        return false;
    }

    private NBFCRequestDTO<?> getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto, DocType docName) {
        try {
            UgroDocumentUploadRequest docUploadRequest = UgroDocumentUploadRequest.builder()
                    .leadId(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())
                    .documentType(getDocumentId(docName))
                    .metaData(UgroDocumentUploadRequest.MetaData.builder()
                            .docType(docName.getContentType())
                            .fileName(docName.name() + docName.getFileExtension())
                            .url("URL".equalsIgnoreCase(docName.getContentType()) ? getFileBlob(lenderAssociationDetailsRequestDto.getApplicationId(), docName, lenderAssociationDetailsRequestDto.getCKycResponseDto(), null) : null)
                            .data("RAW_DATA".equalsIgnoreCase(docName.getContentType()) ? getFileBlob(lenderAssociationDetailsRequestDto.getApplicationId(), docName, lenderAssociationDetailsRequestDto.getCKycResponseDto(), null) : null)
                            .build())
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(lenderAssociationDetailsRequestDto.getApplicationId())
                    .productName("LENDING")
                    .lender(lenderAssociationDetailsRequestDto.getLendingApplication().getLender())
                    .payload(docUploadRequest)
                    .build();
        } catch (Exception e) {
            log.error("UGRO: Exception while creating docUpload payload for applicationId: {}, {}, {}", lenderAssociationDetailsRequestDto.getLendingApplication(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getDocumentId(DocType docType) {
        switch (docType) {
            case SELFIE:
                return "SELFIE";
            case LOAN_DOCUMENTS_MERGED:
                return "LOAN_AGREEMENT";
            case DIGILOCKER_AADHAAR_XML:
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
                        return LenderAssociationStatus.SELFIE_UPLOAD_SUCCESS;
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
        switch (fileBlob) {
            case SELFIE:
                return cKycResponseDto.getSelfieString();
            case DIGILOCKER_AADHAAR_XML:
                return cKycResponseDto.getPoaString();
            case LOAN_DOCUMENTS_MERGED: {
                List<String> documentList = new ArrayList<>();
                documentList.add(lendingKfs.getApplicationFormDocFile());
                documentList.add(lendingKfs.getSanctionLoanAgreementDocFile());
                documentList.add(lendingKfs.getKfsDocFile());
                documentList.add(lendingKfs.getLoaDocFile());
                documentList.add(lendingKfs.getAuthorizationLetterDocFile());
                return docUploadUtils.mergeUnsignedDocs(applicationId, documentList);
            }
            case KEY_FACT_STATEMENT:
                key = lendingKfs.getKfsDocFile();
                break;
            case LOAN_AGREEMENT:
                key = lendingKfs.getSanctionLoanAgreementDocFile();
                break;
            default:
                return null;
        }
        return docUploadUtils.getS3PresignedUrlFromKey(key);
    }

    public boolean invokeAdditionalDocUpload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String docType) {
        try {
            NBFCRequestDTO<?> documentUploadRequest = getAdditionalDocPayload(lendingApplication, lendingApplicationLenderDetails, DocType.valueOf(docType));
            if (ObjectUtils.isEmpty(documentUploadRequest) || ObjectUtils.isEmpty(documentUploadRequest.getPayload())) {
                log.info("UGRO: error in doc upload payload for applicationId: {}", lendingApplication.getId());
                return false;
            }

            NBFCResponseDTO<?> nbfcResponseDto = lenderAPIGateway.invokeStage(documentUploadRequest, LenderAssociationStages.DOC_UPLOAD);
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                return true;
            }
        } catch (Exception e) {
            log.error("UGRO: exception occurred while invoking doc upload for {} {} {} {}", docType, lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private NBFCRequestDTO<?> getAdditionalDocPayload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, DocType docName) {
        try {
            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lendingKfs)) {
                throw new RuntimeException("UGRO: Unable to fetch lending kfs and loan agreement documents for application " + lendingApplication.getId());
            }
            UgroDocumentUploadRequest docUploadRequest = UgroDocumentUploadRequest.builder()
                    .leadId(lendingApplicationLenderDetails.getLeadId())
                    .documentType(getDocumentId(docName))
                    .metaData(UgroDocumentUploadRequest.MetaData.builder()
                            .docType(docName.getContentType())
                            .fileName(docName.name() + docName.getFileExtension())
                            .url("URL".equalsIgnoreCase(docName.getContentType()) ? getFileBlob(lendingApplication.getId(), docName, null, lendingKfs) : null)
                            .data("RAW_DATA".equalsIgnoreCase(docName.getContentType()) ? getFileBlob(lendingApplication.getId(), docName, null, lendingKfs) : null)
                            .build())
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(lendingApplication.getLender())
                    .payload(docUploadRequest)
                    .build();
        } catch (Exception e) {
            log.error("UGRO: Exception while creating docUpload payload for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }
}
