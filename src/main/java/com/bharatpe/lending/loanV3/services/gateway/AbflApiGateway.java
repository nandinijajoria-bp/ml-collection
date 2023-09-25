package com.bharatpe.lending.loanV3.services.gateway;

import com.bharatpe.lending.common.dto.RepaymentRequestDto;
import com.bharatpe.lending.common.util.ConfigResolver;
import com.bharatpe.lending.common.util.LendingHmacCalculator;
import com.bharatpe.lending.loanV3.dto.*;
import com.bharatpe.lending.loanV3.services.INbfcLenderGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class AbflApiGateway extends INbfcLenderGateway {

    @Autowired
    LendingHmacCalculator lendingHmacCalculator;

    @Autowired
    ConfigResolver configResolver;

    @Autowired
    RestTemplate restTemplate;

    @Value("${nbfc.bre.api:api/v3/lender/bureau-check}")
    String nbfcBreUrl;

    @Value("${nbfc.baseurl.v3.api:https://api-nbfc-uat.bharatpe.in/}")
    String nbfcBaseUrl;

    @Value("${nbfc.kyc.api:api/v3/lender/kyc}")
    String nbfcKycUrl;

    @Value("${nbfc.sanction.api:api/v3/lender/sanction}")
    String nbfcSanctionUrl;

    @Value("${nbfc.docupload.api:api/v3/lender/document-upload}")
    String nbfcDocUploadUrl;

    @Value("${nbfc.regulatory.api:api/v3/lender/regulatory-details}")
    String nbfcRegDataUploadUrl;

    @Value("${nbfc.digital.api:api/v3/lender/digital}")
    String nbfcDigitalDataUploadUrl;

    @Value("${nbfc.foreclosureamt.api:api/v3/lender/foreclosure-details}")
    String nbfcForeClosureAmtUrl;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NbfcLenderGateway nbfcLenderGateway;

    public BreApiResponseDto invokeBre(BreApiRequestDto breRequestDto) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(breRequestDto), BreApiResponseDto.class,nbfcBaseUrl+nbfcBreUrl);
        } catch (JsonProcessingException e) {
            log.error("exception occurred while processing bre api call to nbfc svc for {}",breRequestDto, e);
        }
        return null;
    }

    @Override
    public SanctionWrapperApiResponse invokeSanction(SanctionWrapperApiRequestDto sanctionWrapperApiRequestDto) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(sanctionWrapperApiRequestDto), SanctionWrapperApiResponse.class,nbfcBaseUrl+nbfcSanctionUrl);
        } catch (JsonProcessingException e) {
            log.error("exception occurred while processing sanction api call to nbfc svc for {}",sanctionWrapperApiRequestDto, e);
        }
        return null;
    }


    public KycApiResponseDto invokeKyc(KycRequestApiDto kycRequestDto) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(kycRequestDto), KycApiResponseDto.class,nbfcBaseUrl+nbfcKycUrl);
        } catch (JsonProcessingException e) {
            log.error("exception occurred while processing kyc api call to nbfc svc for {}",kycRequestDto, e);
        }
        return null;
    }

    public DocUploadApiResponse invokeDocUpload(DocUploadApiRequestDto docUploadApiRequestDto) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(docUploadApiRequestDto), DocUploadApiResponse.class,nbfcBaseUrl+nbfcDocUploadUrl);
        } catch (JsonProcessingException e) {
            log.error("exception occurred while processing doc upload api call to nbfc svc for {}",docUploadApiRequestDto, e);
        }
        return null;
    }
    public RegulatoryApiResponseDto invokeRegDataUpload(RegulatoryApiRequestDto regulatoryApiRequestDto) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(regulatoryApiRequestDto), RegulatoryApiResponseDto.class,nbfcBaseUrl+nbfcRegDataUploadUrl);
        } catch (JsonProcessingException e) {
            log.error("exception occurred while processing regulatory data upload api call to nbfc svc for {}",regulatoryApiRequestDto, e);
        }
        return null;
    }

    public DigitalDataUploadResponse invokeDigitalDataUpload(DigitalDataUploadRequest digitalDataUploadRequest) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(digitalDataUploadRequest), DigitalDataUploadResponse.class,nbfcBaseUrl+nbfcDigitalDataUploadUrl);
        } catch (JsonProcessingException e) {
            log.error("exception occurred while processing digital data upload api call to nbfc svc for {}",digitalDataUploadRequest, e);
        }
        return null;
    }

    public ForeClosureAmountResponse fetchDueForeclosureAmount(ForeclosureAmountRequest foreclosureAmountRequest) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(foreclosureAmountRequest), ForeClosureAmountResponse.class,nbfcBaseUrl+nbfcForeClosureAmtUrl);
        } catch (JsonProcessingException e) {
            log.error("exception occurred while fetching foreclosure amt to nbfc svc for {}",foreclosureAmountRequest, e);
        }
        return null;
    }

}