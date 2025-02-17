
package com.bharatpe.lending.loanV3.services.associationsV2.oxyzo.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.LenderAssociationStatus;
import com.bharatpe.lending.common.enums.LendingEnum;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV3.config.OxyzoConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.oxyzo.OxyzoAdditionalDocUploadRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.oxyzo.OxyzoCommonResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.oxyzo.OxyzoDocUploadResponseDTO;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.*;

@Slf4j
@Service
public class OxyzoDocUploadService {

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DocUploadUtils docUploadUtils;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    OxyzoConfig oxyzoConfig;

    @Transactional
    public boolean invokeAdditionalDocUpload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String docType) {
        try {
            NBFCRequestDTO documentUploadRequest = getAdditionalDocPayload(lendingApplication.getId(), lendingApplicationLenderDetails, DocType.valueOf(docType));
            if (Objects.isNull(documentUploadRequest) || Objects.isNull(documentUploadRequest.getPayload())) {
                log.info("error in doc upload payload of Oxyzo for applicationId: {}", lendingApplication.getId());
                return false;
            }
            log.info("request body {}", new ObjectMapper().writeValueAsString(documentUploadRequest));
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(documentUploadRequest, LenderAssociationStages.DOC_UPLOAD);
            log.info("docUpload response of OXYZO from nbfc for docTYpe: {} {} with applicationId: {}", nbfcResponseDto, docType, lendingApplication.getId());
            if (Objects.nonNull(nbfcResponseDto) && nbfcResponseDto.getSuccess() && Objects.nonNull(nbfcResponseDto.getData())) {

                OxyzoCommonResponseDTO commonResponseDTO = objectMapper.convertValue(nbfcResponseDto.getData(), OxyzoCommonResponseDTO.class);

                if (oxyzoConfig.getSuccessErrorCode().equalsIgnoreCase(commonResponseDTO.getErrorCode())) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("exception occurred while invoking doc upload of OXYZO for {} {} {} {}", docType, lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private NBFCRequestDTO getAdditionalDocPayload(Long applicationId, LendingApplicationLenderDetails lendingApplicationLenderDetails, DocType docName) {
        try {
            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(applicationId, lendingApplicationLenderDetails.getLender());
            if (ObjectUtils.isEmpty(lendingKfs)) {
                throw new RuntimeException("Unable to fetch lending kfs and loan agreement documents for merchant");
            }

            OxyzoAdditionalDocUploadRequestDTO docUploadRequest = OxyzoAdditionalDocUploadRequestDTO.builder()
                    .loanId(lendingApplicationLenderDetails.getLeadId())
                    .loanAgreement(getLoanDocsMergedUrl(lendingKfs, applicationId))
                    .build();
            return NBFCRequestDTO.builder()
                    .applicationId(applicationId)
                    .productName("LENDING")
                    .lender(LendingEnum.LENDER.OXYZO.name())
                    .payload(docUploadRequest)
                    .build();
        } catch (Exception e) {
            log.error("Exception while creating docUpload payload of OXYZO for applicationId: {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getLoanDocsMergedUrl(LendingKfs lendingKfs, Long applicationId){
        try{
            String kfsDocFile = lendingKfs.getKfsDocFile();
            String loanAgreementDocFile = lendingKfs.getSanctionLoanAgreementDocFile();
            return docUploadUtils.mergeUnsignedDocs(applicationId, kfsDocFile, loanAgreementDocFile);
        } catch(Exception e) {
            log.error("Exception while creating merged loan docs of OXYZO for applicationId: {}, {}, {}", applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));

        }
        return null;
    }



}