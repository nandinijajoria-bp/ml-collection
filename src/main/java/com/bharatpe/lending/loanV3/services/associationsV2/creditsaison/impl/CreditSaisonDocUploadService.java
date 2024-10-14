package com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.impl;


import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.dao.LendingKfsDao;
import com.bharatpe.lending.entity.LendingKfs;
import com.bharatpe.lending.handlers.S3BucketHandler;
import com.bharatpe.lending.loanV3.config.CreditSaisonConfig;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.creditsasion.CreditSasionDocumentUploadRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.creditsasion.CreditSasionDocumentUploadResponseDTO;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.org.apache.xpath.internal.operations.Bool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Objects;

@Slf4j
@Service
public class CreditSaisonDocUploadService {

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Lazy
    @Autowired
    CreditSaisonConfig csConfig;

    @Autowired
    LendingKfsDao lendingKfsDao;

    @Autowired
    S3BucketHandler s3BucketHandler;

    @Value("${aws.s3.bucket:loan-document}")
    private String bucket;

    @Autowired
    CreditSaisonFetchSignedDocService creditSaisonFetchSignedDocService;

    @Transactional
    public boolean invokeAdditionalDocUpload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, String docType){
        try {
            LendingKfs lendingKfs = lendingKfsDao.findTop1ByApplicationIdAndLenderOrderByIdDesc(lendingApplication.getId(), lendingApplicationLenderDetails.getLender());
            if(ObjectUtils.isEmpty(lendingKfs.getSignedKfsDocUrl()) || ObjectUtils.isEmpty(lendingKfs.getSignedSanctionDocUrl())) {
               Boolean fetchDocStatus = creditSaisonFetchSignedDocService.invokeFetchSignedDocs(lendingApplication, DocType.valueOf(docType));
               if(!fetchDocStatus){
                   log.info("CS: error in fetching signed doc of creditsaison for applicationId: {}", lendingApplication.getId());
                   return fetchDocStatus;
               }
            }

            NBFCRequestDTO documentUploadRequest= getPayload(lendingApplication, lendingApplicationLenderDetails, DocType.valueOf(docType), lendingKfs);
            if (Objects.isNull(documentUploadRequest) || Objects.isNull(documentUploadRequest.getPayload())) {
                log.info("CS: error in doc upload payload of creditsaison for applicationId: {}", lendingApplication.getId());
                return false;
            }
            log.info("CS: request body {}", new ObjectMapper().writeValueAsString(documentUploadRequest));
            NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(documentUploadRequest, LenderAssociationStages.DOC_UPLOAD);

            CreditSasionDocumentUploadResponseDTO creditSasionDocumentUploadResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), CreditSasionDocumentUploadResponseDTO.class);
            log.info("CS: docUpload response of creditsaison from nbfc for docTYpe: {} {} with applicationId: {}", nbfcResponseDto, docType, lendingApplication.getId());
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())
                    && csConfig.getSyncInProgressStatus().equalsIgnoreCase(creditSasionDocumentUploadResponseDTO.getStatus())
                    && csConfig.getSyncDocMessage().equalsIgnoreCase(creditSasionDocumentUploadResponseDTO.getMessage())
            ) {
                return true;
            }
        } catch (Exception e) {
            log.error("CS: exception occurred while invoking doc upload of creditsaison for {} {} {} {}", docType, lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return false;
    }

    private NBFCRequestDTO getPayload(LendingApplication lendingApplication, LendingApplicationLenderDetails lendingApplicationLenderDetails, DocType docType, LendingKfs lendingKfs) {
        try {
            LinkedHashMap<String, Object> identifiers = new LinkedHashMap<>();
            identifiers.put("partnerLoanId", lendingApplication.getExternalLoanId());

            if(ObjectUtils.isEmpty(lendingKfs)) {
                throw new RuntimeException("CS: Unable to upload lending kfs or loan agreement documents for application " + lendingApplication.getId());
            }
            return NBFCRequestDTO.builder()
                    .applicationId(lendingApplication.getId())
                    .lender(lendingApplicationLenderDetails.getLender())
                    .productName(csConfig.getLendingProduct())
                    .identifier(identifiers)
                    .payload(CreditSasionDocumentUploadRequestDTO.builder()
                            .partnerLoanId(lendingApplication.getExternalLoanId())
                            .appForm(
                                    CreditSasionDocumentUploadRequestDTO.AppForm.builder()
                                            .documents(Arrays.asList(
                                                    CreditSasionDocumentUploadRequestDTO.AppForm.Document.builder()
                                                            .url(getDocUrl(docType, lendingKfs))
                                                            .metaData(CreditSasionDocumentUploadRequestDTO.AppForm.Document.MetaData.builder()
                                                                    .key(csConfig.getMetaDataKey())
                                                                    .value(lendingApplication.getIp())
                                                                    .build())
                                                            .fileName(docType.name() + docType.getFileExtension())
                                                            .docType(getDocIdentifier(docType))
                                                            .build()
                                            ))
                                            .build()
                            )
                            .build())
                    .build();
                } catch (Exception e) {
                    log.info("CS: exception occurred while parsing response data of CreditSaison doc upload {} for {} {}, {}", docType, lendingApplication.getId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
                }
        return null;
    }
    private String getDocUrl(DocType fileBlob, LendingKfs lendingKfs) {
        switch (fileBlob) {
            case KEY_FACT_STATEMENT:
                return  getS3PresignedUrlFromKey(lendingKfs.getSignedKfsDocUrl());
            case LOAN_AGREEMENT:
                return getS3PresignedUrlFromKey(lendingKfs.getSignedSanctionDocUrl());
            default:
                return null;
        }
    }

    private String getDocIdentifier(DocType docType) {
        switch (docType) {
            case KEY_FACT_STATEMENT :
                return csConfig.getDocTypeSanctionWrapper();
            case LOAN_AGREEMENT :
                return csConfig.getDocTypeLoanAgreement();
            default:
                return "";
        }
    }

    private String getS3PresignedUrlFromKey(String key) {
        log.info("key to fetch from aws: {}", key);
        return ObjectUtils.isEmpty(key) ? "" : s3BucketHandler.getPreSignedPublicURLWithExceptionHandled(key, bucket);
    }

}
