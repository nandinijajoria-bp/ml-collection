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

    @Value("${nbfc.digitalSign.api:api/v3/lender/digital-sign}")
    String nbfcDigitalSignUrl;

    @Value("${nbfc.rps.api:api/v3/lender/repayment-schedule}")
    String nbfcRpsUrl;

    @Value("${nbfc.pennydrop.api:api/v3/lender/penny-drop}")
    String nbfcPennyDropUrl;

    @Value("${nbfc.eKyc.api:api/v3/lender/eKyc}")
    String nbfcEKycUrl;

    @Value("${nbfc.eKyc.status.api:api/v3/lender/eKyc-status-check}")
    String nbfcEKycStatusUrl;

    @Value("${nbfc.kyc.validity.api:api/v3/lender/kyc-validity}")
    String nbfcKycValidityUrl;

    @Value("${nbfc.pennydrop.read.timeout:30000}")
    int nbfcPennyDropReadTimeout;


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

    public AbflTopupRpsResponseDTO fetchRepaymentSchedule(AbflTopupRpsRequestDTO abflTopupRpsRequest) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(abflTopupRpsRequest), AbflTopupRpsResponseDTO.class,nbfcBaseUrl+nbfcRpsUrl);
        } catch (JsonProcessingException e) {
            log.error("exception occurred while processing repayment schedule api call to nbfc svc for {}",abflTopupRpsRequest, e);
        }
        return null;
    }

    public AbflDigiSignResponseDTO invokeDigiSign(AbflDigiSignRequestDTO abflDigiSignRequest) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(abflDigiSignRequest), AbflDigiSignResponseDTO.class,nbfcBaseUrl+nbfcDigitalSignUrl);
        } catch (JsonProcessingException e) {
            log.error("exception occurred while invoking digiSign api call to nbfc svc for {}",abflDigiSignRequest, e);
        }
        return null;
    }

    public ABFLPennyDropResponseDTO invokePennyDrop(ABFLPennyDropRequestDTO pennyDropRequestDTO) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(pennyDropRequestDTO), ABFLPennyDropResponseDTO.class,nbfcBaseUrl+nbfcPennyDropUrl, nbfcPennyDropReadTimeout);
        } catch (JsonProcessingException e) {
            log.error("exception occurred while fetching pennyDropRequestDTO to nbfc svc for {}", pennyDropRequestDTO, e);
        }
        return null;
    }

    public EKycApiResponseDto invokeEKyc(EKycRequestApiDto eKycRequestApiDto) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(eKycRequestApiDto), EKycApiResponseDto.class,nbfcBaseUrl+nbfcEKycUrl);
        } catch (JsonProcessingException e) {
            log.error("exception occurred while processing eKyc api call to nbfc svc for {}",eKycRequestApiDto, e);
        }
        return null;
    }

    public EKycCallbackResponseDto invokeEKycStatusCheck(EKycStatusCheckRequestApiDto eKycStatusCheckRequest) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(eKycStatusCheckRequest), EKycCallbackResponseDto.class,nbfcBaseUrl+nbfcEKycStatusUrl);
        } catch (JsonProcessingException e) {
            log.error("exception occurred while processing eKyc status check api call to nbfc svc for {}",eKycStatusCheckRequest, e);
        }
        return null;
    }

    public KycValidityApiResponseDto invokeKycValidity(KycValidityRequestApiDto kycValidityRequest) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(kycValidityRequest), KycValidityApiResponseDto.class,nbfcBaseUrl+nbfcKycValidityUrl);
        } catch (JsonProcessingException e) {
            log.error("exception occurred while processing eKyc status check api call to nbfc svc for {}",kycValidityRequest, e);
        }
        return null;
    }

}