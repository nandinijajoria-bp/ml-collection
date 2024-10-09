package com.bharatpe.lending.loanV3.services.gateway;

import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Slf4j
@Service
public class lenderAPIGateway implements ILenderAPIGateway{
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

    @Value("${nbfc.getLoan.api:api/v3/lender/generate-document}")
    String docGenerateUrl;

    @Value("${nbfc.riskDecision.api:api/v3/lender/bureau-check}")
    String riskDecisionUrl;

    @Value("${nbfc.rps.api:api/v3/lender/repayment-schedule}")
    String rpsGenerateUrl;

    @Value("${nbfc.createClient.api:api/v3/lender/create-client}")
    String createClientUrl;

    @Value("${nbfc.postConsent.api:api/v3/lender/post-consent}")
    String postConsentUrl;

    @Value("${nbfc.digitalSign.api:api/v3/lender/digital-sign}")
    String digitalSignUrl;

    @Value("${nbfc.nach.mandate.api:api/v3/lender/mandate-registration}")
    String nbfcNachMandateUrl;

    @Value("${nbfc.rps.api:api/v3/lender/foreclosure-details}")
    String foreclosureFetchUrl;

    @Value("${nbfc.kyc.api:api/v3/lender/kyc}")
    String kycUrl;

    @Value("${nbfc.kyc.api:api/v3/lender/accept-offer}")
    String acceptOfferUrl;

    @Value("${nbfc.getLoan.api:api/v3/lender/download-document}")
    String downloadDocumentUrl;

    @Value("${nbfc.loanPreview.api:api/v3/lender/loan-preview}")
    String loanPreviewUrl;

    @Value("${nbfc.pennydrop.api:api/v3/lender/penny-drop}")
    String nbfcPennyDropUrl;


    @Override
    public NBFCResponseDTO invokeStage(NBFCRequestDTO nbfcRequestDto, LenderAssociationStages lenderAssociationStage) {
        try {
            return nbfcLenderGateway.invoke(objectMapper.writeValueAsString(nbfcRequestDto), NBFCResponseDTO.class, getUrl(lenderAssociationStage));
        } catch (Exception e) {
            log.error("exception occurred while processing {} api call to nbfc for lender {} for {} {},{}", lenderAssociationStage.name(), nbfcRequestDto.getLender(), nbfcRequestDto, e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
        return null;
    }


    private String getUrl (LenderAssociationStages lenderAssociationStages) {
        switch (lenderAssociationStages.name()) {
            case "CREATE_LEAD":
                return nbfcBaseUrl+ createLeadUrl;
            case "AADHAR_UPLOAD":
            case "SELFIE_UPLOAD":
            case "SHOP_PHOTO_UPLOAD":
            case "SHOP_STOCK_PHOTO_UPLOAD":
            case "DOC_UPLOAD":
                return nbfcBaseUrl+nbfcDocUploadUrl;
            case "UPDATE_LEAD":
                return nbfcBaseUrl+updateLeadUrl;
            case "DOC_GENERATE":
                return nbfcBaseUrl+docGenerateUrl;
            case "BRE":
                return nbfcBaseUrl+riskDecisionUrl;
            case "RPS":
                return nbfcBaseUrl+rpsGenerateUrl;
            case "CREATE_CLIENT":
                return nbfcBaseUrl + createClientUrl;
            case "POST_CONSENT":
                return nbfcBaseUrl + postConsentUrl;
            case "DIGI_SIGN":
                return nbfcBaseUrl + digitalSignUrl;
            case "NACH_MANDATE":
                return nbfcBaseUrl + nbfcNachMandateUrl;
            case "FORECLOSURE_FETCH":
                return nbfcBaseUrl+foreclosureFetchUrl;
            case "KYC":
                return nbfcBaseUrl+kycUrl;
            case "ACCEPT_OFFER":
                return nbfcBaseUrl+acceptOfferUrl;
            case "DOWNLOAD_DOCUMENT":
                return nbfcBaseUrl+downloadDocumentUrl;
            case "LOAN_PREVIEW":
                return nbfcBaseUrl+loanPreviewUrl;
            case "PENNY_DROP":
                return nbfcBaseUrl+nbfcPennyDropUrl;
            default:
                return null;
        }
    }
}
