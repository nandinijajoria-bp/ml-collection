package com.bharatpe.lending.loanV3.services.associationsV2.capri.impl;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.dao.LendingApplicationLenderDetailsDao;
import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.common.enums.Status;
import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.request.capri.CapriFetchSignedDocsRequestDTO;
import com.bharatpe.lending.loanV3.dto.response.capri.CapriFetchSignedDocsResponseDTO;
import com.bharatpe.lending.loanV3.dto.response.capri.CapriSignDocsDTO;
import com.bharatpe.lending.loanV3.services.gateway.ILenderAPIGateway;
import com.bharatpe.lending.loanV3.utils.DocUploadUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class CapriFetchSignedDocService {

    @Autowired
    LendingApplicationLenderDetailsDao lendingApplicationLenderDetailsDao;

    @Autowired
    ILenderAPIGateway lenderAPIGateway;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DocUploadUtils docUploadUtils;

    public boolean invokeFetchSignedDocs(LendingApplication lendingApplication) {
        if (ObjectUtils.isEmpty(lendingApplication)) {
            log.info("invalid params for Capri fetch signed docs");
            return false;
        }
        LendingApplicationLenderDetails lendingApplicationLenderDetails = lendingApplicationLenderDetailsDao.findTop1LendingApplicationLenderDetailsByApplicationIdAndStatusAndLenderOrderByIdDesc(lendingApplication.getId(), Status.ACTIVE.name(), lendingApplication.getLender());
        if(ObjectUtils.isEmpty(lendingApplicationLenderDetails)) {
            log.info("lender record not exist for Capri with applicationId {} ", lendingApplication.getId());
            return false;
        }
        List<String> docList = Arrays.asList("KFS", "SANCTION_AGREEMENT");
        CapriSignDocsDTO capriSignDocsDTO = new CapriSignDocsDTO();
        for(String docType : docList) {
           fetchDoc(lendingApplication.getId(), lendingApplicationLenderDetails.getLeadId(), docType, capriSignDocsDTO);
        }
        if(ObjectUtils.isEmpty(capriSignDocsDTO.getSignedKfs()) && ObjectUtils.isEmpty(capriSignDocsDTO.getSignedSanctionAgreement())) {
            log.info("error in fetching capri signed docs for applicationId  {} ", lendingApplication.getId());
            return false;
        }
        docUploadUtils.saveESignedDocs(lendingApplication.getId(), capriSignDocsDTO.getSignedKfs(), capriSignDocsDTO.getSignedSanctionAgreement());
        return true;
    }

    private void fetchDoc(Long applicationId, String leadId, String docType, CapriSignDocsDTO capriSignDocsDTO) {
        NBFCRequestDTO nbfcRequestDto = NBFCRequestDTO.builder()
                .productName("LENDING")
                .lender(Lender.CAPRI.name())
                .applicationId(applicationId)
                .payload(CapriFetchSignedDocsRequestDTO.builder().leadId(leadId).tagIdentifier(getDocIdentifier(docType)).build())
                .build();
        NBFCResponseDTO nbfcResponseDto = lenderAPIGateway.invokeStage(nbfcRequestDto, LenderAssociationStages.DOWNLOAD_DOCUMENT);
        try {
            if (!ObjectUtils.isEmpty(nbfcResponseDto) && nbfcResponseDto.getSuccess() && !ObjectUtils.isEmpty(nbfcResponseDto.getData())) {
                CapriFetchSignedDocsResponseDTO capriFetchSignedDocsResponse = objectMapper.readValue(objectMapper.writeValueAsString(nbfcResponseDto.getData()), CapriFetchSignedDocsResponseDTO.class);
                if("KFS".equalsIgnoreCase(docType)) {
                    capriSignDocsDTO.setSignedKfs(capriFetchSignedDocsResponse.getPdfContent());
                } else {
                    capriSignDocsDTO.setSignedSanctionAgreement(capriFetchSignedDocsResponse.getPdfContent());
                }
            }
        } catch (
                Exception e) {
            log.info("exception occurred while parsing response data of Capri fetch signed doc {} for {} {}, {}", docType, applicationId, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }

    private Integer getDocIdentifier(String docType) {
        switch (docType) {
            case "KFS" :
                return 123;
            case "SANCTION_AGREEMENT" :
                return 124;
            default:
                return 0;
        }
    }
}
