package com.bharatpe.lending.loanV3.services.associationsV2.smfg.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.enums.KycDocType;
import com.bharatpe.lending.loanV3.config.SmfgConfig;
import com.bharatpe.lending.loanV3.dto.BusinessDocsDTO;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.dto.request.smfg.SmfgDocumentUploadRequest;
import com.bharatpe.lending.loanV3.dto.response.smfg.SmfgDocUploadResponseDto;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.associations.piramal.CommonService;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Objects;
import java.util.PriorityQueue;

@Slf4j
@Service
public class SmfgDocUploadService {

    @Autowired
    KycUtils kycUtils;

    @Autowired
    CommonService commonService;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DocUploadUtils docUploadUtils;

    @Autowired
    SmfgConfig smfgConfig;
    @Autowired
    private LendingKfsDao lendingKfsDao;

    @Transactional
    public boolean invokeDocUpload(LenderAssociationDetailsRequestDto lenderAssociationDetailsDto, String docType) {
        DocType docName = DocType.valueOf(docType);
        LendingApplicationLenderDetails lendingApplicationLenderDetails  = lenderAssociationDetailsDto.getLendingApplicationLenderDetails();
        try {
            if (DocType.BUSINESS_DOC.equals(docName) && (ObjectUtils.isEmpty(lendingApplicationLenderDetails.getDataUploadStatus()) || !lendingApplicationLenderDetails.getDataUploadStatus().equalsIgnoreCase(smfgConfig.getPslFlagTrue()))) {
                log.info("SMFG : returning business docs upload true as PSL flag is false for applicationId : {}", lenderAssociationDetailsDto.getApplicationId());
                return true;
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(docType);
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(docUploadUtils.getStatusForDocumentUpload(docName, "PENDING").name());
            commonService.manageApplicationState(lenderAssociationDetailsDto);

            if (ObjectUtils.isEmpty(lenderAssociationDetailsDto.getCKycResponseDto())) {
                lenderAssociationDetailsDto.setCKycResponseDto(kycUtils.getKycData(lenderAssociationDetailsDto.getMerchantId()));
            }
            NBFCRequestDTO documentUploadRequest = getPayload(lenderAssociationDetailsDto, docName);
            if (Objects.isNull(documentUploadRequest)) {
                log.info("error in creating doc upload payload of SMFG for applicationId: {}", lenderAssociationDetailsDto.getApplicationId());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.PAYLOAD_ERROR.name());
                lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(docUploadUtils.getStatusForDocumentUpload(docName, "FAILED").name());
                commonService.manageApplicationState(lenderAssociationDetailsDto);
                return false;
            }
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(documentUploadRequest, LenderAssociationStages.DOC_UPLOAD);
            log.info("docUpload response of SMFG from nbfc for docTYpe: {} {} with applicationId: {}", nbfcResponseDto, docName, lenderAssociationDetailsDto.getApplicationId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess()) {
                log.info("doc upload request of SMFG success for {} {}", docName, lenderAssociationDetailsDto.getApplicationId());
                SmfgDocUploadResponseDto docUploadResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), SmfgDocUploadResponseDto.class);
                if ("SUCCESS".equalsIgnoreCase(docUploadResponseDTO.getStatus())) {
                    lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(docUploadUtils.getStatusForDocumentUpload(docName, "SUCCESS").name());
                    commonService.manageApplicationState(lenderAssociationDetailsDto);
                    return true;
                }
            }
            lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setLeadStatus(LenderAssociationStatus.ValidationStatus.API_RESPONSE_FAILED.name());
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload of SMFG for {} {} {} {}", docType, lenderAssociationDetailsDto.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        lenderAssociationDetailsDto.getLendingApplicationLenderDetails().setKycStatus(docUploadUtils.getStatusForDocumentUpload(docName, "FAILED").name());
        commonService.manageApplicationState(lenderAssociationDetailsDto);
        return false;
    }

    private NBFCRequestDTO getPayload(LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest, DocType doc) {
        try {
            LendingApplication lendingApplication = lenderAssociationDetailsRequest.getLendingApplication();
            BusinessDocsDTO businessDocs = getBusinessDocs(lendingApplication, doc);
            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(),lendingApplication.getLender());
            SmfgDocumentUploadRequest docUploadRequest = SmfgDocumentUploadRequest.builder()
                    .partnerapplicationid(lendingApplication.getExternalLoanId())
                    .partnerid(smfgConfig.getPartnerId())
                    .vkycinfo(SmfgDocumentUploadRequest.Vkycinfo.builder()
                            .documentInfo(SmfgDocumentUploadRequest.DocumentInfo.builder()
                                    .documentData(docUploadUtils.getFileBlob(doc, lenderAssociationDetailsRequest.getCKycResponseDto(), lendingKfs, null, businessDocs))
                                    .documentName(getDocumentNameMapping(doc))
                                    .documentType(doc.getFileExtension().substring(1))
                                    .ipaddress(lendingApplication.getIp())
                                    .latitude(lendingApplication.getLatitude())
                                    .longitude(lendingApplication.getLongitude()).build())
                            .build()).build();
            return NBFCRequestDTO.builder()
                    .applicationId(lenderAssociationDetailsRequest.getApplicationId())
                    .productName("LENDING")
                    .lender(lendingApplication.getLender())
                    .payload(docUploadRequest).build();
        } catch (Exception e) {
            log.info("Exception while creating docUpload payload of SMFG for applicationId: {}, {}, {}", lenderAssociationDetailsRequest.getLendingApplication(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private BusinessDocsDTO getBusinessDocs(LendingApplication lendingApplication, DocType doc) throws Exception {
        BusinessDocsDTO businessDocs = null;
        if (DocType.BUSINESS_DOC.equals(doc)) {
            PriorityQueue<BusinessDocsDTO> businessDocsQueue = kycUtils.getBusinessDocData(lendingApplication.getMerchantId(), "SMFG", KycDocType.UDYAM_CERTIFICATE.name());
            if (businessDocsQueue.isEmpty() || businessDocsQueue.peek() == null || ObjectUtils.isEmpty(businessDocsQueue.peek().getPdfUrl())) {
                log.info("SMFG : PSL flag true but document is empty for application id: {}", lendingApplication.getId());
                throw new Exception("SMFG : PSL flag true but document is empty for application id" + lendingApplication.getId());
            }
            businessDocs = businessDocsQueue.poll();
        }
        return businessDocs;
    }

    private String getDocumentNameMapping(DocType docType) {
        switch (docType.name()) {
            case "DIGILOCKER_AADHAAR_XML":
                return smfgConfig.getAadhaarDocType();
            case "SELFIE":
                return smfgConfig.getSelfieDocType();
            case "BUSINESS_DOC":
                return smfgConfig.getUdyamDocType();
            case "AUDIT_TRAIL_DOC":
                return smfgConfig.getAuditTrailDocType();
            default:
                return null;
        }
    }

}
