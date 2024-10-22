package com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.common.query.dao.LendingKfsSlaveDao;
import com.bharatpe.lending.common.query.entity.LendingKfsSlave;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.creditsasion.CreditSaisonFetchSignedDocsRequestDTO;
import com.bharatpe.lending.loanV3.dto.request.creditsasion.CreditSaisonSignDocsDTO;
import com.bharatpe.lending.loanV3.dto.response.creditsasion.CreditSaisonFetchSignedDocsResponseDTO;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

@Slf4j
@Service
public class CreditSaisonFetchSignedDocService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DocUploadUtils docUploadUtils;

    @Autowired
    LendingKfsSlaveDao lendingKfsSlaveDao;

    public boolean invokeFetchSignedDocs(LendingApplication lendingApplication, DocType docType ) {
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("CS: invalid params for creditsaison fetch signed docs");
            return false;
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());

        if(ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("CS: lender record not exist for creditsaison with applicationId {} ", lendingApplication.getId());
            return false;
        }
        CreditSaisonSignDocsDTO creditSaisonSignDocsDTO = new CreditSaisonSignDocsDTO();

        fetchDoc(lendingApplication.getExternalLoanId(), lendingApplication.getId(), lendingApplication.getExternalLoanId(), docType, creditSaisonSignDocsDTO);

        if(ObjectUtils.isEmpty(creditSaisonSignDocsDTO.getSignedKfs()) && ObjectUtils.isEmpty(creditSaisonSignDocsDTO.getSignedSanctionAgreement())) {
            log.info("CS: error in fetching creditsaison signed docs for applicationId  {} ", lendingApplication.getId());
            return false;
        }
        docUploadUtils.saveESignedDocs(lendingApplication.getId(), creditSaisonSignDocsDTO.getSignedKfs(), creditSaisonSignDocsDTO.getSignedSanctionAgreement());
        return true;
    }

    private void fetchDoc(String externalLoanId, Long applicationId, String partnerLoanId, DocType docType, CreditSaisonSignDocsDTO creditSaisonSignDocsDTO) {
        LendingKfsSlave lendingKfs = lendingKfsSlaveDao.findTop1ByApplicationIdOrderByIdDesc(applicationId);
        LinkedHashMap<String, Object> identifiers = new LinkedHashMap<>();
        identifiers.put("partnerLoanId", externalLoanId);
        NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(Lender.CREDITSAISON.name())
                .applicationId(applicationId)
                .identifier(identifiers)
                .payload(CreditSaisonFetchSignedDocsRequestDTO.builder()
                        .partnerLoanId(partnerLoanId)
                        .url(getDocUrl(docType, lendingKfs))
                        .documentType(getDocIdentifier(docType))
                        .build())
                .build();
        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.DOWNLOAD_DOCUMENT);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                CreditSaisonFetchSignedDocsResponseDTO creditSaisonFetchSignedDocsResponseDTO = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), CreditSaisonFetchSignedDocsResponseDTO.class);
                if(DocType.KEY_FACT_STATEMENT.name().equalsIgnoreCase(docType.name())) {
                    creditSaisonSignDocsDTO.setSignedKfs(creditSaisonFetchSignedDocsResponseDTO.getUrl());
                } else {
                    creditSaisonSignDocsDTO.setSignedSanctionAgreement(creditSaisonFetchSignedDocsResponseDTO.getUrl());
                }
            }
        } catch (
                Exception e) {
            log.info("CS: exception occurred while parsing response data of creditsaison fetch signed doc {} for {} {}, {}", docType, applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    private String getDocIdentifier(DocType docType) {
        switch (docType) {
            case KEY_FACT_STATEMENT:
                return "schedule-letter";
            case LOAN_AGREEMENT:
                return "loan-agreement";
            default:
                return "";
        }
    }

    private String getDocUrl(DocType docType, LendingKfsSlave lendingKfsSlave) {
        switch (docType) {
            case KEY_FACT_STATEMENT:
                return lendingKfsSlave.getKfsDocUrl();
            case LOAN_AGREEMENT:
                return lendingKfsSlave.getSanctionLoanAgreementDocUrl();
            default:
                return "";
        }
    }
}
