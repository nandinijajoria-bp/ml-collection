package com.bharatpe.lending.loanV3.services.gateway;

import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.loanV3.dto.DocUploadApiRequestDto;
import com.bharatpe.lending.loanV3.dto.DocUploadApiResponse;
import com.bharatpe.lending.loanV3.dto.piramal.*;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import com.bharatpe.lending.loanV3.services.gateway.piramal.ILenderGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class PiramalApiGateway extends ILenderGateway {

    @Autowired
    NbfcLenderGateway nbfcLenderGateway;

    @Autowired
    ObjectMapper objectMapper;


    @Value("${nbfc.baseurl.v3.api:https://api-nbfc-uat.bharatpe.in/}")
    String nbfcBaseUrl;

    @Value("${nbfc.docupload.api:api/v3/lender/document-upload}")
    String nbfcDocUploadUrl;

    @Value("${nbfc.createLead.api:api/v3/lender/create-lead}")
    String createLeadUrl;

    @Value("${nbfc.updateLead.api:api/v3/lender/update-lead}")
    String updateLeadUrl;

    @Value("${nbfc.riskDecision.api:api/v3/lender/bureau-check}")
    String riskDecisionUrl;

    @Value("${nbfc.getLoan.api:api/v3/lender/get-lead}")
    String getLoanUrl;

    @Value("${nbfc.insurance.api:api/v3/lender/insurance-premiums}")
    String insurancePremiumUrl;

    @Value("${nbfc.eKyc.api:api/v3/lender/eKyc}")
    String nbfcEKycUrl;

    @Value("${nbfc.eKyc.status.api:api/v3/lender/eKyc-status-check}")
    String nbfcEKycStatusUrl;

    @Override
    public NbfcResponseDto invokeStage(NbfcRequestDto nbfcRequestDto, LenderAssociationStages.PiramalAssociationStages piramalAssociationStages) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(nbfcRequestDto), NbfcResponseDto.class, getUrl(piramalAssociationStages));
        } catch (JsonProcessingException e) {
            log.error("exception occurred while processing {} api call to nbfc for piramal for {} {}", piramalAssociationStages.name(), nbfcRequestDto, e.getMessage());
        }
        return null;
    }

    @Override
    public NbfcResponseDto invokeStageViaParams(Map<String,String> requestMap, LenderAssociationStages.PiramalAssociationStages piramalAssociationStages, String pathVars) {
        try {
            requestMap.put("loanAccountNumber",pathVars);
            return nbfcLenderGateway.invokeWithParams(objectMapper.writeValueAsString(requestMap), NbfcResponseDto.class, getUrl(piramalAssociationStages) , HttpMethod.GET);
        } catch (JsonProcessingException e) {
            log.error("exception occurred while processing {} api call to nbfc for piramal for {} {} {}",  piramalAssociationStages.name(), requestMap, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }

    private String getUrl (LenderAssociationStages.PiramalAssociationStages piramalAssociationStages) {
        switch (piramalAssociationStages.name()) {
            case "LEAD_CREATION":
                return nbfcBaseUrl+ createLeadUrl;
            case "AADHAR_UPLOAD":
            case "SELFIE_UPLOAD":
            case "DOC_UPLOAD":
                return nbfcBaseUrl+nbfcDocUploadUrl;
            case "UPDATE_LEAD":
                return nbfcBaseUrl+updateLeadUrl;
            case "RISK_DECISION":
                return nbfcBaseUrl+riskDecisionUrl;
            case "GET_LOAN_DETAILS":
                return nbfcBaseUrl+getLoanUrl;
            case "INSURANCE_PREMIUM":
                return nbfcBaseUrl + insurancePremiumUrl;
            case "EKYC":
                return nbfcBaseUrl + nbfcEKycUrl;
            case "EKYC_STATUS":
                return nbfcBaseUrl + nbfcEKycStatusUrl;
            default:
                return null;
        }
    }
}
