package com.bharatpe.lending.loanV3.services.associationsV2;

import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.loanV3.dto.DisbursalCallbackCommonDTO;
import com.bharatpe.lending.loanV3.dto.LenderEdIScheduleResponseDTO;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl.*;
import com.bharatpe.lending.loanV3.services.associationsV2.usfb.impl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AssociationServiceUtil {
    @Autowired
    LeadCreateService leadCreateService;
    
    @Autowired
    DocUploadService docUploadService;
    
    @Autowired
    LeadUpdateService leadUpdateService;
    
    @Autowired
    LoanOnboardingService loanOnboardingService;

    @Autowired
    DisbursalCallbackService disbursalCallbackService;

    @Autowired
    RepaymentService repaymentService;

    @Autowired
    TLCreateClientService tlCreateClientService;

    @Autowired
    TLCreateLeadService tlCreateLeadService;

    @Autowired
    TLUpdateLeadService tlUpdateLeadService;

    @Autowired
    TLDocUploadService tlDocUploadService;

    @Autowired
    TLKycService tlKycService;

    @Autowired
    TLBreService tlBreService;

    @Autowired
    TrillionRepaymentService trillionRepaymentService;

    @Autowired
    TLDisbursalCallbackService tlDisbursalCallbackService;

    @Autowired
    TLConsentPostingService tlConsentPostingService;

    @Autowired
    TLDigitalSignService tlDigitalSignService;

    @Autowired
    TLNachMandateService tlNachMandateService;

    @Autowired
    TLRepaymentScheduleService tlRepaymentScheduleService;

    public Boolean invokeCreateLeadService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        switch (lender) {
            case "USFB":
                return leadCreateService.invokeCreateLead(lenderAssociationDetailsRequest);
            case "TRILLIONLOANS":
                return tlCreateLeadService.invokeCreateLead(lenderAssociationDetailsRequest);
            default:
                return false;
        }
    }

    public Boolean invokeDocUploadService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest, String docType) {
        switch (lender) {
            case "USFB":
                return docUploadService.invokeDocUpload(lenderAssociationDetailsRequest, docType);
            case "TRILLIONLOANS":
                return tlDocUploadService.invokeDocUpload(lenderAssociationDetailsRequest, docType);
            default:
                return false;
        }
    }

    public Boolean invokeLeadUpdateService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        switch (lender) {
            case "USFB":
                return leadUpdateService.invokeUpdateLead(lenderAssociationDetailsRequest);
            case "TRILLIONLOANS":
                return tlUpdateLeadService.invokeUpdateLead(lenderAssociationDetailsRequest);
            default:
                return false;
        }
    }

    public Boolean invokeBREService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        switch (lender) {
            case "USFB":
                return loanOnboardingService.invokeLoanOnboardingFLow(lenderAssociationDetailsRequest);
            case "TRILLIONLOANS":
                return tlBreService.invokeBre(lenderAssociationDetailsRequest);
            default:
                return false;
        }
    }

    public Boolean invokeAdditionalDocUpload(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest, String docType) {
        switch (lender) {
            case "USFB":
                return docUploadService.invokeAdditionalDocUpload(lenderAssociationDetailsRequest.getLendingApplication(), lenderAssociationDetailsRequest.getLendingApplicationLenderDetails(), docType);
            case "TRILLIONLOANS":
                return tlDocUploadService.invokeAdditionalDocUpload(lenderAssociationDetailsRequest.getLendingApplication(), lenderAssociationDetailsRequest.getLendingApplicationLenderDetails(), docType);
            default:
                return false;
        }
    }

    public LenderEdIScheduleResponseDTO invokeRepaymentScheduleService(String lender, Long applicationId) {
        switch (lender) {
            case "USFB":
                return repaymentService.invokeRpsGenerate(applicationId);
            case "TRILLIONLOANS":
                return tlRepaymentScheduleService.invokeRpsGenerate(applicationId);
            default:
                return null;
        }
    }

    public DisbursalCallbackCommonDTO handleDisbursalCallbackResponse(String lender, NBFCResponseDTO nbfcResponseDTO) {
        switch (lender) {
            case "USFB":
                return disbursalCallbackService.handleCallbackResponse(nbfcResponseDTO);
            case "TRILLIONLOANS":
                return tlDisbursalCallbackService.parseCallbackResponse(nbfcResponseDTO);
            default:
                return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        }
    }

    public Boolean handleBreCallback(String lender, NBFCResponseDTO nbfcResponseDTO) {
        switch (lender) {
            case "USFB":
                return loanOnboardingService.processLoanOnboardingCallback(nbfcResponseDTO);
            default:
                return false;
        }
    }

    public NBFCRequestDTO foreclosureReceiptRequest(String lender, Long applicationId, LendingLedger lendingLedger) {
        switch (lender) {
            case "USFB":
                return repaymentService.getForeclosureReceiptRequest(applicationId, lendingLedger);
            case "TRILLIONLOANS":
                return trillionRepaymentService.getForeclosureReceiptRequest(applicationId, lendingLedger);
            default:
                return null;
        }
    }

    public Double getForeclosureAmount(String lender, Long applicationId) {
        switch(lender) {
            case "USFB" :
                return 0D;
            default:
                return 0D;
        }
    }

    public Boolean invokeCreateClientService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        switch (lender) {
            case "TRILLIONLOANS":
                return tlCreateClientService.invokeCreateClient(lenderAssociationDetailsRequest);
            default:
                return false;
        }
    }

    public Boolean handleKycCallback(String lender, NBFCResponseDTO nbfcResponseDTO) {
        switch (lender) {
            case "TRILLIONLOANS":
                return tlKycService.processKycCallback(nbfcResponseDTO);
            default:
                return false;
        }
    }

    public Boolean invokeConsentPostingService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        switch (lender) {
            case "TRILLIONLOANS":
                return tlConsentPostingService.invokeConsentPosting(lenderAssociationDetailsRequest);
            default:
                return false;
        }
    }

    public Boolean invokeDigitalSignService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest, String docType) {
        switch (lender) {
            case "TRILLIONLOANS":
                return tlDigitalSignService.invokeDigitalSign(lenderAssociationDetailsRequest, docType);
            default:
                return false;
        }
    }

    public Boolean handleDigitalSignCallback(String lender, NBFCResponseDTO nbfcResponseDTO) {
        switch (lender) {
            case "TRILLIONLOANS":
                return tlDigitalSignService.processDigitalSignCallback(nbfcResponseDTO);
            default:
                return false;
        }
    }

    public boolean invokeNachMandateService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        switch (lender) {
            case "TRILLIONLOANS":
                return tlNachMandateService.invokeNachMandate(lenderAssociationDetailsDto);
            default:
                return false;
        }
    }
}
