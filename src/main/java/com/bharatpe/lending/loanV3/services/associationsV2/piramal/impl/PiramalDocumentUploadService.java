package com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.common.query.dao.LendingKfsSlaveDao;
import com.bharatpe.lending.common.query.entity.LendingKfsSlave;
import com.bharatpe.lending.common.util.DateTimeUtil;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.piramal.*;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.validations.DocUploadValidationLayer;
import com.bharatpe.lending.loanV3.services.gateway.piramal.ILenderGateway;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.*;

@Service
@Slf4j
public class PiramalDocumentUploadService {

    @Autowired
    KycUtils kycUtils;

    @Autowired
    ILenderGateway iLenderGateway;

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    CommonService commonService;

    @Autowired
    DocUploadValidationLayer docUploadValidationLayer;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    S3BucketHandler s3BucketHandler;

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
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(getStageforDocumentUpload(docName).name());
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName,"PENDING").name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }
            if (docUploadValidationLayer.isInValidPayload(lenderAssociationDetailsDto.getCKycResponseDto())) {
                log.info("invalid response from downstream api : {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName,"FAILED").name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName,"FAILED"));
                return false;
            }
            NbfcRequestDto documentUploadDTO = getPayload(lenderAssociationDetailsDto, docName);
            if (Objects.isNull(documentUploadDTO)) {
                log.info("error in doc upload payload for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName,"FAILED").name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName,"FAILED"));
                return false;
            }
            NbfcResponseDto nbfcResponseDto = iLenderGateway.invokeStage(documentUploadDTO, getStageforDocumentUpload(docName));
            log .info("docUpload response from nbfc for docTYpe: {} {} with applicationId: {}", nbfcResponseDto, docName, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess()) {
                log.info("doc upload request success for {} {}", docName, lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName,"SUCCESS").name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return true;
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload for {} {} {} {}", docType, lenderAssociationDetailsDto.getApplicationId(), e.getMessage(),
                    Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName,"FAILED").name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName,"FAILED"));
        return false;
    }

    private DocType getDocName(String docType) {

        switch (docType) {
            case "SELFIE_UPLOAD":
                return DocType.SELFIE;
            case "AADHAR_UPLOAD":
                return DocType.DIGILOCKER_AADHAAR_XML;

            default:
               return DocType.SELFIE;
        }
    }

    private LenderAssociationStages.PiramalAssociationStages getStageforDocumentUpload(DocType docType) {
        switch (docType.name()) {
            case "DIGILOCKER_AADHAAR_XML":
                return LenderAssociationStages.PiramalAssociationStages.AADHAR_UPLOAD;
            case "SELFIE":
                return LenderAssociationStages.PiramalAssociationStages.SELFIE_UPLOAD;
            default:
                return null;
        }
    }
    private LenderAssociationStatus getStatusforDocumentUpload(DocType docType, String currentStage) {
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

    private NbfcRequestDto getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, DocType docType) {
        // call validation Layer
        DocumentUploadDTO documentUploadDTO = new DocumentUploadDTO();
        documentUploadDTO.setUploadDate(DateTimeUtil.getDateInFormat(new Date(), "yyyy-MM-dd'T'HH:mm:ss.000'Z'"));
        documentUploadDTO.setVersion(1);
        documentUploadDTO.setLeadId(lenderAssociationDetailsDto.getLendingApplicationLenderDetails().getLeadId());
        DocumentUploadDTO.MetaData metadata = DocumentUploadDTO.MetaData.builder()
                .type(docType.getContentType())
                .data("RAW_DATA".equalsIgnoreCase(docType.getContentType()) ? getFileBlob(docType, lenderAssociationDetailsDto.getCKycResponseDto(), null, null) : null)
                .url("URL".equalsIgnoreCase(docType.getContentType()) ? getFileBlob(docType, lenderAssociationDetailsDto.getCKycResponseDto(), null, null) : null)
                .build();
        List<DocumentUploadDTO.DocumentList> documents = new ArrayList<>();
        DocumentUploadDTO.DocumentList documentList = new DocumentUploadDTO.DocumentList();
        documentList.setType(docType.name());
        documentList.setDocumentCategory(docType.getCategory());
        documentList.setDocumentSubCategory(docType.getSubCategory());
        documentList.setFileName(lenderAssociationDetailsDto.getApplicationId().toString() + "_" + docType.name() + docType.getFileExtension());
        documentList.setMetadata(metadata);
        documentList.setUploadDate(DateTimeUtil.getDateInFormat(new Date(), "yyyy-MM-dd'T'HH:mm:ss.000'Z'"));
        documents.add(documentList);
        documentUploadDTO.setDocumentList(documents);
        documentUploadDTO.setGeoLocation(DocumentUploadDTO.GeoLocation.builder()
                        .latitude(lenderAssociationDetailsDto.getLendingApplication().getLatitude())
                        .longitude(lenderAssociationDetailsDto.getLendingApplication().getLongitude())
                .build());

        log.info("document upload dto for applicationId: {} {}", documentUploadDTO, lenderAssociationDetailsDto.getApplicationId());
        return NbfcRequestDto.builder().productName("LENDING").payload(documentUploadDTO).lender(Lender.PIRAMAL.name()).applicationId(lenderAssociationDetailsDto.getApplicationId()).build();
    }

    private String getFileBlob(DocType fileBlob, CKycResponseDto cKycResponseDto, LendingKfs lendingKfs, LendingShopDocuments lendingShopDocument) {
        String key = null;
        switch (fileBlob.name()) {
            case "DIGILOCKER_AADHAAR_XML":
                return cKycResponseDto.getPoaString();
            case "SELFIE":
                return cKycResponseDto.getSelfieString();

            case "LOAN_AGREEMENT":
            case "SANCTION_LETTER":
                key =  lendingKfs.getSanctionLoanAgreementDocFile();
                break;
            case "KEY_FACT_STATEMENT":
                key =  lendingKfs.getKfsDocFile();
                break;
            case "SHOP_PHOTO":
                key =  lendingShopDocument.getProofFrontSide();
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

    public boolean invokeAdditionalDocUpload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String docType) {
        try {

            DocumentUploadDTO documentUploadDTO = getAdditionalDocPayload(lendingApplication, lendingApplicationLenderDetails, DocType.valueOf(docType));

            if (Objects.isNull(documentUploadDTO) || Objects.isNull(documentUploadDTO.getDocumentList().get(0).getMetadata().getUrl())) {
                log.info("error in doc upload payload for applicationId: {}", lendingApplication.getId());
                return false;
            }
            log.info("request body {}", new ObjectMapper().writeValueAsString(documentUploadDTO));
            NbfcRequestDto docUploadDto = NbfcRequestDto.builder().productName("LENDING").payload(documentUploadDTO).lender(LendingEnum.LENDER.PIRAMAL.name()).applicationId(lendingApplication.getId()).build();
            NbfcResponseDto nbfcResponseDto = iLenderGateway.invokeStage(docUploadDto, LenderAssociationStages.PiramalAssociationStages.DOC_UPLOAD);
            log.info("docUpload response from nbfc for docTYpe: {} {} with applicationId: {}", nbfcResponseDto, docType, lendingApplication.getId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess()) {
                return true;
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload for {} {} {} {}", docType, lendingApplication.getId(), e.getMessage(),
                    Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private DocumentUploadDTO getAdditionalDocPayload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, DocType docType) {

        LendingShopDocuments lendingShopDocument = null;
        LendingKfs lendingKfs = null;
        if (docType.equals(DocType.SHOP_PHOTO) || docType.equals(DocType.SHOP_STOCK)) {
            String docName = docType.equals(DocType.SHOP_PHOTO) ? "SHOP-FRONT" : "SHOP-STOCK";
            lendingShopDocument = lendingShopDocumentsDao.findTop1ByMerchantIdAndApplicationIdAndProofTypeOrderByIdDesc
                    (lendingApplication.getMerchantId(), lendingApplication.getId(), docName);
            if (ObjectUtils.isEmpty(lendingShopDocument)) {
                return new DocumentUploadDTO();
            }
        }
        log.info("lending shop doc {} {}", docType, lendingShopDocument);
        if (DocType.KEY_FACT_STATEMENT.equals(docType) || DocType.LOAN_AGREEMENT.equals(docType) || DocType.SANCTION_LETTER.equals(docType)) {
            lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplication.getLender());
            if (ObjectUtils.isEmpty(lendingKfs)) {
                return new DocumentUploadDTO();
            }
            log.info("lendingkfs: {}", lendingKfs);
        }

        if (DocType.SHOP_STOCK.equals(docType)) {
            docType = DocType.SHOP_PHOTO;
        }
        DocumentUploadDTO documentUploadDTO = new DocumentUploadDTO();
        documentUploadDTO.setUploadDate(DateTimeUtil.getDateInFormat(new Date(), "yyyy-MM-dd'T'HH:mm:ss.000'Z'"));
        documentUploadDTO.setVersion(1);
        documentUploadDTO.setLeadId(lendingApplicationLenderDetails.getLeadId());
        DocumentUploadDTO.MetaData metadata = DocumentUploadDTO.MetaData.builder()
                .type(docType.getContentType())
                .url(getFileBlob(docType, null, lendingKfs, lendingShopDocument))
                .build();
        List<DocumentUploadDTO.DocumentList> documents = new ArrayList<>();
        DocumentUploadDTO.DocumentList documentList = new DocumentUploadDTO.DocumentList();
        documentList.setType(docType.name());
        documentList.setDocumentCategory(docType.getCategory());
        documentList.setDocumentSubCategory(docType.getSubCategory());
        documentList.setFileName(lendingApplication.getId().toString() + "_" + docType.name() + docType.getFileExtension());
        documentList.setMetadata(metadata);
        documentList.setUploadDate(DateTimeUtil.getDateInFormat(new Date(), "yyyy-MM-dd'T'HH:mm:ss.000'Z'"));
        documents.add(documentList);
        documentUploadDTO.setDocumentList(documents);
        documentUploadDTO.setGeoLocation(DocumentUploadDTO.GeoLocation.builder()
                .latitude(lendingApplication.getLatitude())
                .longitude(lendingApplication.getLongitude())
                .build());

        log.info("document upload dto for applicationId: {} {}", documentUploadDTO, lendingApplication.getId());
        return documentUploadDTO;
    }
}
