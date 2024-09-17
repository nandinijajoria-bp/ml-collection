package com.bharatpe.lending.loanV3.services.associationsV2.payu.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.dao.LendingShopDocumentsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.entity.LendingShopDocuments;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dao.LendingGstDao;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.payu.*;
import com.bharatpe.lending.loanV3.dto.response.payu.*;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
public class PayUDocUploadService {

    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    LendingShopDocumentsDao lendingShopDocumentsDao;

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
                log.info("invalid response from downstream api for payU docUpload: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "FAILED").name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName, "FAILED"));
                return false;
            }
            NBFCRequestDTO documentUploadRequest = getPayload(lenderAssociationDetailsDto, docName);
            if (Objects.isNull(documentUploadRequest)) {
                log.info("error in doc upload payload of payU for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "FAILED").name());
                commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName, "FAILED"));
                return false;
            }
            NBFCResponseDTO nbfcResponseDTO = lenderAPIGateway.invokeStage(documentUploadRequest, LenderAssociationStages.valueOf(docType));
            log.info("docUpload response of payU from nbfc for docType: {} {} with applicationId: {}", nbfcResponseDTO, docName, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDTO) && nbfcResponseDTO.getSuccess() && Objects.nonNull(nbfcResponseDTO.getData())) {

                PayUCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDTO.getData(), PayUCommonResponseDTO.class);

                PayUDocUploadResponseDTO uploadResponseDTO =  objectMapper.convertValue(commonResponseDTO.getApiResponse(), PayUDocUploadResponseDTO.class);

                if ("SUCCESS".equalsIgnoreCase(commonResponseDTO.getApiStatus())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "SUCCESS").name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload of payU for {} {} {} {}", docType, lenderAssociationDetailsDto.getApplicationId(), e.getMessage(),
                    Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(getStatusforDocumentUpload(docName, "FAILED").name());
        commonService.manageApplicationStateAndModifyLender(lenderAssociationDetailsDto, getStatusforDocumentUpload(docName, "FAILED"));
        return false;
    }

    private DocType getDocName(String docType) {
        switch (docType) {
            case "AADHAR_UPLOAD":
                return DocType.DIGILOCKER_AADHAAR_XML;
            case "SELFIE_UPLOAD":
                return DocType.SELFIE;
            case "SHOP_PHOTO_UPLOAD":
                return DocType.SHOP_PHOTO;
            case "SHOP_STOCK_PHOTO_UPLOAD":
                return DocType.SHOP_STOCK;
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
            case "SHOP_PHOTO":
            case "SHOP_STOCK":
                switch (currentStage) {
                    case "FAILED":
                        return LenderAssociationStatus.SHOP_PHOTO_UPLOAD_FAILED;
                    case "SUCCESS":
                        return LenderAssociationStatus.SHOP_PHOTO_UPLOAD_SUCCESS;
                    default:
                        return LenderAssociationStatus.SHOP_PHOTO_UPLOAD_PENDING;
                }
            default:
                return null;
        }
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequestDto, DocType docName) {
        try {

            LendingShopDocuments lendingShopDocuments = lendingShopDocumentsDao.findTopByMerchantIdAndProofTypeOrderByIdDesc(lenderAssociationDetailsRequestDto.getLendingApplication().getMerchantId(), "SHOP-FRONT");
            LendingShopDocuments lendingShopStockDocuments = lendingShopDocumentsDao.findTopByMerchantIdAndProofTypeOrderByIdDesc(lenderAssociationDetailsRequestDto.getLendingApplication().getMerchantId(), "SHOP-STOCK");

            if(ObjectUtils.isEmpty(lendingShopDocuments)) {
                throw new RuntimeException("Unable to fetch lending shop documents for merchant");
            }

            String documentId = getDocumentId(docName);

            PayUDocUploadRequestDTO docUploadRequest = PayUDocUploadRequestDTO.builder()
                    .docTypeId(documentId)
                    .liveness("D2000".equalsIgnoreCase(documentId))
                    .applicationId(lenderAssociationDetailsRequestDto.getLendingApplicationLenderDetails().getLeadId())
                    .metaData(PayUDocUploadRequestDTO.MetaData.builder()
                            .docType(docName.getContentType())
                            .fileName(docName.name() + docName.getFileExtension())
                            .url("URL".equalsIgnoreCase(docName.getContentType()) ? getFileBlob(docName, lenderAssociationDetailsRequestDto.getCKycResponseDto(), null, lendingShopDocuments, lendingShopStockDocuments) : null)
                            .data("RAW_DATA".equalsIgnoreCase(docName.getContentType()) ? getFileBlob(docName, lenderAssociationDetailsRequestDto.getCKycResponseDto(), null, lendingShopDocuments, lendingShopStockDocuments) : null)
                            .build())
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(lenderAssociationDetailsRequestDto.getApplicationId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.PAYU.name())
                    .payload(docUploadRequest)
                    .build();
        } catch (Exception e) {
            log.error("Exception while creating docUpload payload of payU for applicationId: {}, {}, {}", lenderAssociationDetailsRequestDto.getLendingApplication(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getDocumentId(DocType docType) {
        switch (docType.name()) {
            case "SELFIE":
                return "D2000";
            case "KEY_FACT_STATEMENT_LOAN_AGREEMENT_MERGED":
                return "D9024";
            case "DIGILOCKER_AADHAAR_XML":
                return "D9223";
            case "SHOP_PHOTO":
            case "SHOP_STOCK":
                return "D1011";
            default:
                return null;
        }
    }

    private String getFileBlob(DocType fileBlob, CKycResponseDto cKycResponseDto, LendingKfs lendingKfs, LendingShopDocuments lendingShopDocument, LendingShopDocuments lendingShopStockDocument)
             {
        String key = null;
        switch (fileBlob.name()) {
            case "SELFIE":
                return cKycResponseDto.getSelfieString();
            case "SHOP_PHOTO":
                key =  lendingShopDocument.getProofFrontSide();
                break;
            case "SHOP_STOCK":
                key = lendingShopStockDocument.getProofFrontSide();
                break;
            case "DIGILOCKER_AADHAAR_XML":
                return cKycResponseDto.getPoaString();
            case "KEY_FACT_STATEMENT":
                key = lendingKfs.getKfsDocFile();
                break;
            case "LOAN_AGREEMENT":
                key = lendingKfs.getSanctionLoanAgreementDocFile();
                break;
            case "MITC":
                key = lendingKfs.getMitcDocFile();
                break;
            case "GTC":
                key = lendingKfs.getGtcDocFile();
                break;
            case "LOA":
                key = lendingKfs.getLoaDocFile();
                break;
            case "APPLICATION_FORM":
                key = lendingKfs.getApplicationFormDocFile();
                break;
            default:
                return null;
        }
        return docUploadUtils.getS3PresignedUrlFromKey(key);
    }

    public boolean invokeAdditionalDocUpload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String docType) {
        try {

            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplicationLenderDetails.getLender());
            if (ObjectUtils.isEmpty(lendingKfs)) {
                throw new RuntimeException("Unable to fetch lending kfs and loan agreement documents for merchant");
            }

            NBFCRequestDTO getLoanDocsRequest = getLoanDocsRequestDTO(lendingApplication.getId(), lendingApplicationLenderDetails, lendingKfs);

            if (Objects.isNull(getLoanDocsRequest) || Objects.isNull(getLoanDocsRequest.getPayload())) {
                log.info("error in getLoanDocs API of PayU for applicationId: {}", lendingApplication.getId());
                return false;
            }

            log.info("request body {}", new ObjectMapper().writeValueAsString(getLoanDocsRequest));
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(getLoanDocsRequest, LenderAssociationStages.DOC_UPLOAD);
            log.info("getLoanDocs response of PayU from nbfc for docType: {} {} with applicationId: {}", nbfcResponseDto, docType, lendingApplication.getId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {
                PayUCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), PayUCommonResponseDTO.class);

                PayULoanDocsUploadResponseDTO getLoanDocsResponseDTO =  objectMapper.convertValue(commonResponseDTO.getApiResponse(), PayULoanDocsUploadResponseDTO.class);

                if ("SUCCESS".equalsIgnoreCase(commonResponseDTO.getApiStatus())) {
                    return invokeSignDocs(lendingApplication,lendingApplicationLenderDetails, docType, getLoanDocsResponseDTO);
                }
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload of PayU for {} {} {} {}", docType, lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private NBFCRequestDTO getLoanDocsRequestDTO(Long applicationId, LendingApplicationLenderDetails lendingApplicationLenderDetails, LendingKfs lendingKfs) {
        try {
            PayUDocUploadRequestDTO getLoanDocsRequest = PayUDocUploadRequestDTO.builder()
                    .applicationId(lendingApplicationLenderDetails.getLeadId())
                    .documentList(getDocumentList(lendingKfs))
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(applicationId)
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.PAYU.name())
                    .payload(getLoanDocsRequest)
                    .build();
        } catch (Exception e) {
            log.error("Exception while creating getRequiredDocs payload of PayU for applicationId: {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private List<PayUDocUploadRequestDTO.DocumentList> getDocumentList(LendingKfs lendingKfs){

        List<String> docList = Arrays.asList("SANCTION_LETTER", "KFS_LETTER", "MITC", "GTC", "LOA", "APPLICATION_FORM");

        List<PayUDocUploadRequestDTO.DocumentList> documentList = new ArrayList<>();

        for( String docs: docList ){

            documentList.add(PayUDocUploadRequestDTO.DocumentList.builder()
                            .documentType(docs)
                            .fileUrl(getFile(docs, lendingKfs))
                    .build());
        }

        return documentList;

    }

    private String getFile(String doc, LendingKfs lendingKfs) {

        DocType docName;

        switch (doc){
            case "SANCTION_LETTER":
                docName = DocType.LOAN_AGREEMENT;
                break;
            case "KFS_LETTER":
                docName = DocType.KEY_FACT_STATEMENT;
                break;
            case "MITC":
                docName = DocType.MITC;
                break;
            case "GTC":
                docName = DocType.GTC;
                break;
            case "LOA":
                docName = DocType.LOA;
                break;
            case "APPLICATION_FORM":
                docName = DocType.APPLICATION_FORM;
                break;
            default:
                docName = null;
        }

       return getFileBlob(docName, null, lendingKfs, null, null);

    }

    public boolean invokeSignDocs(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String docType, PayULoanDocsUploadResponseDTO getLoanDocsResponseDTO) {
        try {
            List<PayULoanDocsUploadResponseDTO.DocumentList> documentsList = getLoanDocsResponseDTO.getDocumentList();

            if (documentsList == null || documentsList.isEmpty()) {
                log.info("Loan documents list is empty for PayU");
                return false;
            }

            List<String> docList = Arrays.asList("SANCTION_LETTER", "KFS_LETTER", "MITC", "GTC", "LOA", "APPLICATION_FORM");

            List<PayUSignDocsRequestDTO.RequestDetails> requestDetailsList = new ArrayList<>();

            for( String doc: docList ) {

                Optional<PayULoanDocsUploadResponseDTO.DocumentList> document = findDocument(documentsList, doc);

                if (!document.isPresent() || document.get().getDocumentId() == null ||  document.get().getDocumentId().isEmpty()) {
                    log.info("Document or documentId not found for PayU - {}", doc);
                    return false;
                } else {
                    requestDetailsList.add(getSignDocsRequestDetails(lendingApplication, lendingApplicationLenderDetails, document.get().getDocumentId(), doc ));
                }
            }

            NBFCRequestDTO getSignDocsRequest = getSignDocsPayload(lendingApplication, lendingApplicationLenderDetails, requestDetailsList);
            if (getSignDocsRequest == null || getSignDocsRequest.getPayload() == null) {
                log.info("Error in invokeSignDocs API of PayU for applicationId: {}", lendingApplication.getId());
                return false;
            }

            log.info("Request body {}", getSignDocsRequest);

            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(getSignDocsRequest, LenderAssociationStages.DIGI_SIGN);
            log.info("Sign Docs response of PayU from nbfc for docType: {} {} with applicationId: {}", nbfcResponseDto, docType, lendingApplication.getId());

            if (nbfcResponseDto != null && nbfcResponseDto.getSuccess() && nbfcResponseDto.getData() != null) {
                PayUCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), PayUCommonResponseDTO.class);

                return "SUCCESS".equalsIgnoreCase(commonResponseDTO.getApiStatus());
            }

        } catch (Exception e) {
            log.error("Exception occurred while invoking sign doc API of PayU for {} {} {} {}", docType, lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private Optional<PayULoanDocsUploadResponseDTO.DocumentList> findDocument(List<PayULoanDocsUploadResponseDTO.DocumentList> documentsList, String documentType) {
        return documentsList.stream()
                .filter(doc -> documentType.equalsIgnoreCase(doc.getDocumentType()))
                .findFirst();
    }

    private NBFCRequestDTO getSignDocsPayload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, List<PayUSignDocsRequestDTO.RequestDetails> requestDetailsList) {
        try {
            PayUSignDocsRequestDTO requestData =  PayUSignDocsRequestDTO.builder()
                    .applicationId(lendingApplicationLenderDetails.getLeadId())
                    .requestDetails(requestDetailsList)
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.PAYU.name())
                    .payload(requestData)
                    .build();
        } catch (Exception e) {
            log.error("exception occurred while getting sign docs request payload for createLead for applicationId: {}, {}, {}", lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private PayUSignDocsRequestDTO.RequestDetails getSignDocsRequestDetails(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String documentId, String docType){
        return PayUSignDocsRequestDTO.RequestDetails.builder()
                .documentDetails(getDocumentDetails(documentId, docType))
                .acceptanceDetails(getAcceptanceDetails(lendingApplication, lendingApplicationLenderDetails))
                .build();
    }

    private PayUSignDocsRequestDTO.RequestDetails.DocumentDetails getDocumentDetails(String documentId, String docType){

        return PayUSignDocsRequestDTO.RequestDetails.DocumentDetails.builder()
                .documentId(documentId)
                .type(docType)
                .build();
    }

    private PayUSignDocsRequestDTO.RequestDetails.AcceptanceDetails getAcceptanceDetails(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails){

        return PayUSignDocsRequestDTO.RequestDetails.AcceptanceDetails.builder()
                .signingDetails(
                        PayUSignDocsRequestDTO.RequestDetails.AcceptanceDetails.SigningDetails.builder()
                                .date(LocalDate.now().toString())
                                .time(LocalTime.now().toString())
                                .ip(lendingApplication.getIp())
                                .modeOfSigning("mobile_otp")
                                .otp(lendingApplicationLenderDetails.getAgreementOtp())
                                .build()
                )
                .build();
    }

}
