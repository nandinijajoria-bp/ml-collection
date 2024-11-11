package com.bharatpe.lending.loanV3.services.associationsV2;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.common.entities.LendingLedger;
import com.bharatpe.lending.loanV3.consumer.KycRequestKafka;
import com.bharatpe.lending.loanV3.dto.DisbursalCallbackCommonDTO;
import com.bharatpe.lending.loanV3.dto.LenderEdIScheduleResponseDTO;
import com.bharatpe.lending.loanV3.dto.NBFCRequestDTO;
import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.dto.piramal.LenderAssociationDetailsRequestDto;
import com.bharatpe.lending.loanV3.enums.DocType;
import com.bharatpe.lending.loanV3.services.associationsV2.capri.impl.*;
import com.bharatpe.lending.loanV3.services.associationsV2.muthoot.impl.*;
import com.bharatpe.lending.loanV3.services.associationsV2.payu.impl.*;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.EKycService;
import com.bharatpe.lending.loanV3.services.associationsV2.smfg.impl.*;
import com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.impl.*;
import com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.impl.*;
import com.bharatpe.lending.loanV3.services.associationsV2.usfb.impl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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

    @Autowired
    MFLeadService mfLeadService;

    @Autowired
    MFKycService mfKycService;

    @Autowired
    MFBreService mfBreService;

    @Autowired
    MFDocUploadService mfDocUploadService;

    @Autowired
    MFDisbursalCallbackService mfDisbursalCallbackService;

    @Autowired
    MFForeclosureService mfForeclosureService;

    @Autowired
    MFRepaymentScheduleService mfRepaymentScheduleService;

    @Autowired
    CapriLeadService capriLeadService;

    @Autowired
    CapriCreateClientService capriCreateClientService;

    @Autowired
    CapriDocUploadService capriDocUploadService;

    @Autowired
    CapriBreService capriBreService;

    @Autowired
    CapriNachMandateService capriNachMandateService;

    @Autowired
    CapriDisbursalCallbackService capriDisbursalCallbackService;

    @Autowired
    CapriRepaymentScheduleService capriRepaymentScheduleService;

    @Autowired
    CapriForeclosureService capriForeclosureService;

    @Autowired
    CapriFetchSignedDocService capriFetchSignedDocService;

    @Autowired
    AbflDigiSignService abflDigiSignService;

    @Autowired
    AbflDocGenerateService abflDocGenerateService;

    @Autowired
    CreditSaisonDisbursalCallbackService creditSaisonDisbursalCallbackService;

    @Autowired
    CreditSaisonBREService creditSasionBREService;

    @Autowired
    CreditSaisonKYCService creditSaisonKYCService;

    @Autowired
    CreditSaisonCreateClientService creditSaisonCreateClientService;

    @Autowired
    CreditSaisonPennyDropService creditSaisonPennyDropService;

    @Autowired
    CreditSaisonDocUploadService creditSaisonDocUploadService;

    @Autowired
    CreditSaisonFetchSignedDocService creditSaisonFetchSignedDocService;


    @Lazy
    @Autowired
    KycRequestKafka kycRequestKafka;

    @Autowired
    PayULeadService payULeadService;

    @Autowired
    PayUDocUploadService payUDocUploadService;

    @Autowired
    PayUBreService payUBreService;

    @Autowired
    PayUNachMandateService payUNachMandateService;

    @Autowired
    PayUDisbursalCallbackService payUDisbursalCallbackService;

    @Autowired
    PayUKycService payUKycService;

    @Autowired
    PayURepaymentScheduleService payURepaymentScheduleService;

    @Autowired
    CreditSaisonForeclosureService creditSaisonForeclosureService;

    @Autowired
    PayUForeclosureService payUForeclosureService;

    @Autowired
    EKycService eKycService;

    @Autowired
    TLTopupUndoApproveService tlTopupUndoApproveService;

    @Autowired
    TLTopupDataService tlTopupDataService;

    @Autowired
    TLAddChargeService tlAddChargeService;

    @Autowired
    TLTopupApproveService tlTopupApproveService;

    @Autowired
    SmfgBreService smfgBreService;

    @Autowired
    SmfgDocUploadService smfgDocUploadService;

    @Autowired
    SmfgNachMandateService smfgNachMandateService;

    @Autowired
    SmfgDisbursalCallbackService smfgDisbursalCallbackService;

    @Autowired
    SmfgRpsService smfgRpsService;

    @Autowired
    SmfgForeclosureService smfgForeclosureService;

    public Boolean invokeCreateLeadService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        switch (lender) {
            case "USFB":
                return leadCreateService.invokeCreateLead(lenderAssociationDetailsRequest);
            case "TRILLIONLOANS":
                return tlCreateLeadService.invokeCreateLead(lenderAssociationDetailsRequest);
            case "MUTHOOT":
                return mfLeadService.invokeCreateLead(lenderAssociationDetailsRequest);
            case "CAPRI":
                return capriLeadService.invokeCreateLead(lenderAssociationDetailsRequest);
            case "PAYU":
                return payULeadService.invokeCreateLead(lenderAssociationDetailsRequest);
            default:
                return false;
        }
    }

    public Boolean invokeKycService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        switch (lender) {
            case "MUTHOOT":
                return mfKycService.invokeKyc(lenderAssociationDetailsRequest);
            case "PAYU":
                return payUKycService.invokeKyc(lenderAssociationDetailsRequest);
            case "CREDITSAISON":
                return creditSaisonKYCService.invokeKyc(lenderAssociationDetailsRequest);
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
            case "MUTHOOT":
                return mfDocUploadService.invokeDocUpload(lenderAssociationDetailsRequest, docType);
            case "CAPRI":
                return capriDocUploadService.invokeDocUpload(lenderAssociationDetailsRequest, docType);
            case "PAYU":
                return payUDocUploadService.invokeDocUpload(lenderAssociationDetailsRequest, docType);
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
            case "MUTHOOT":
                return mfLeadService.invokeUpdateLead(lenderAssociationDetailsRequest);
            case "CAPRI":
                return capriLeadService.invokeUpdateLead(lenderAssociationDetailsRequest);
            case "PAYU":
                return payULeadService.invokeUpdateLead(lenderAssociationDetailsRequest);
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
            case "MUTHOOT":
                return mfBreService.invokeBre(lenderAssociationDetailsRequest);
            case "CAPRI":
                return capriBreService.invokeBre(lenderAssociationDetailsRequest);
            case "PAYU":
                return payUBreService.invokeBre(lenderAssociationDetailsRequest);
            case "CREDITSAISON":
                return creditSasionBREService.invokeBre(lenderAssociationDetailsRequest);
            case "SMFG":
                return smfgBreService.invokeBre(lenderAssociationDetailsRequest);
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
            case "MUTHOOT":
                return mfDocUploadService.invokeAdditionalDocUpload(lenderAssociationDetailsRequest.getLendingApplication(), lenderAssociationDetailsRequest.getLendingApplicationLenderDetails(), docType);
            case "CAPRI":
                return capriDocUploadService.invokeAdditionalDocUpload(lenderAssociationDetailsRequest.getLendingApplication(), lenderAssociationDetailsRequest.getLendingApplicationLenderDetails(), docType);
            case "PAYU":
                return payUDocUploadService.invokeAdditionalDocUpload(lenderAssociationDetailsRequest.getLendingApplication(),  lenderAssociationDetailsRequest.getLendingApplicationLenderDetails(), docType);
            case "CREDITSAISON":
                return creditSaisonDocUploadService.invokeAdditionalDocUpload(lenderAssociationDetailsRequest.getLendingApplication(), lenderAssociationDetailsRequest.getLendingApplicationLenderDetails(), docType);
            case "SMFG":
                return smfgDocUploadService.invokeDocUpload(lenderAssociationDetailsRequest, docType);
            default:
                return false;
        }
    }

    public LenderEdIScheduleResponseDTO invokeRepaymentScheduleService(String lender, Long applicationId, Boolean isPreview) {
        switch (lender) {
            case "USFB":
                return repaymentService.invokeRpsGenerate(applicationId);
            case "TRILLIONLOANS":
                return tlRepaymentScheduleService.invokeRpsGenerate(applicationId);
            case "MUTHOOT":
                return mfRepaymentScheduleService.invokeRpsGenerate(applicationId);
            case "CAPRI":
                return capriRepaymentScheduleService.invokeRpsGenerate(applicationId);
            case "PAYU":
                return payURepaymentScheduleService.invokeRpsGenerate(applicationId, isPreview);
            case "SMFG":
                return smfgRpsService.invokeRpsGenerate(applicationId);
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
            case "MUTHOOT":
                return mfDisbursalCallbackService.handleCallbackResponse(nbfcResponseDTO);
            case "CAPRI":
                return capriDisbursalCallbackService.parseCallbackResponse(nbfcResponseDTO);
            case "PAYU":
                return payUDisbursalCallbackService.handleDisbursalCallbackResponse(nbfcResponseDTO);
            case "CREDITSAISON":
                return creditSaisonDisbursalCallbackService.parseCallbackResponse(nbfcResponseDTO);
            case "SMFG":
                return smfgDisbursalCallbackService.handleCallbackResponse(nbfcResponseDTO);
            default:
                return DisbursalCallbackCommonDTO.builder().status(Boolean.FALSE).build();
        }
    }

    public Boolean handleBreCallback(String lender, NBFCResponseDTO nbfcResponseDTO) {
        switch (lender) {
            case "USFB":
                return loanOnboardingService.processLoanOnboardingCallback(nbfcResponseDTO);
            case "MUTHOOT":
                return mfBreService.processMFBreCallback(nbfcResponseDTO);
            case "CAPRI":
                return capriBreService.processBreCallback(nbfcResponseDTO);
            case "TRILLIONLOANS":
                return tlBreService.processBreCallback(nbfcResponseDTO);
            case "PAYU":
                return payUBreService.processBreCallback(nbfcResponseDTO);
            case "CREDITSAISON":
                return creditSasionBREService.processCreditSasionBreCallback(nbfcResponseDTO);
            case "SMFG":
                return smfgBreService.processBreCallback(nbfcResponseDTO);
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
            case "MUTHOOT":
                return null;
            case "CAPRI":
                return capriForeclosureService.getForeclosureReceiptRequest(applicationId, lendingLedger);
            case "PAYU":
                return payUForeclosureService.getForeclosureReceiptRequest(applicationId,lendingLedger);
            case "CREDITSAISON":
                return creditSaisonForeclosureService.getForeclosureReceiptRequest(applicationId, lendingLedger);
            case "SMFG":
                return smfgForeclosureService.getForeclosureReceiptRequest(applicationId,lendingLedger);
            default:
                return null;
        }
    }

    public Double getForeclosureAmount(String lender, Long applicationId) {
        switch(lender) {
            case "USFB" :
                return 0D;
            case"MUTHOOT":
                return mfForeclosureService.getForeclosureDetails(applicationId);
            case "CAPRI":
                return capriForeclosureService.getForeclosureDetails(applicationId);
            case "PAYU":
                return payUForeclosureService.getForeclosureDetails(applicationId);
            case "SMFG":
                return smfgForeclosureService.getForeclosureDetails(applicationId);
            default:
                return 0D;
        }
    }

    public Boolean invokeCreateClientService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        switch (lender) {
            case "TRILLIONLOANS":
                return tlCreateClientService.invokeCreateClient(lenderAssociationDetailsRequest);
            case "CAPRI":
                return capriCreateClientService.invokeCreateClient(lenderAssociationDetailsRequest);
            case "CREDITSAISON":
                return creditSaisonCreateClientService.invokeCreateClient(lenderAssociationDetailsRequest);
            default:
                return false;
        }
    }

    public Boolean handleKycCallback(String lender, NBFCResponseDTO nbfcResponseDTO) {
        switch (lender) {
            case "TRILLIONLOANS":
                return tlKycService.processKycCallback(nbfcResponseDTO);
            case "MUTHOOT":
                return mfKycService.processMFKycCallback(nbfcResponseDTO);
            case "CREDITSAISON":
                return creditSaisonKYCService.processCreditSasionKycCallback(nbfcResponseDTO);
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
            case "MUTHOOT":
                return mfDocUploadService.processMFDocUploadCallback(nbfcResponseDTO);
            case "ABFL":
                return abflDigiSignService.processDigitalSignCallback(nbfcResponseDTO);
            default:
                return false;
        }
    }

    public boolean invokeNachMandateService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        switch (lender) {
            case "TRILLIONLOANS":
                return tlNachMandateService.invokeNachMandate(lenderAssociationDetailsDto);
            case "CAPRI":
                return capriNachMandateService.invokeNachMandate(lenderAssociationDetailsDto);
            case "PAYU":
                return payUNachMandateService.invokeNachMandate(lenderAssociationDetailsDto);
            case "SMFG":
                return smfgNachMandateService.invokeNachMandate(lenderAssociationDetailsDto);
            default:
                return false;
        }
    }

    public boolean invokeDocsGenerateService(String lender, LendingApplication lendingApplication, DocType docType, Boolean preSigned) {
        switch (lender) {
            case "CAPRI":
                return capriFetchSignedDocService.invokeFetchSignedDocs(lendingApplication);
            case "ABFL":
                return abflDocGenerateService.invokeDocGenerate(lendingApplication, docType, preSigned, true);
            default:
                return false;
        }
    }

    public Boolean invokePennyDropService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        switch (lender) {
            case "CREDITSAISON":
                return creditSaisonPennyDropService.invokePennyDrop(lenderAssociationDetailsRequest);
            default:
                return false;
        }
    }

    public boolean invokeEkycStatusCheck(String lender, LendingApplication lendingApplication) {
        switch (lender) {
            case "ABFL":
                return kycRequestKafka.eKycStatusCheck(lendingApplication);
            case "PIRAMAL":
                return eKycService.eKycStatusCheck(lendingApplication);
            default:
                return false;
        }
    }

    public Boolean handleEKycCallback(String lender, NBFCResponseDTO nbfcResponseDTO) {
        switch (lender) {
            case "PIRAMAL":
                return eKycService.processEKycCallback(nbfcResponseDTO);
            default:
                return false;
        }
    }

    public boolean invokeEKyc(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsRequest) {
        switch (lender) {
            case "PIRAMAL":
                return eKycService.invokeEKyc(lenderAssociationDetailsRequest);
            default:
                return false;
        }
    }

    public Boolean handlePennyDropCallback(String lender, NBFCResponseDTO nbfcResponseDTO) {
        switch (lender) {
            case "CREDITSAISON":
                return creditSaisonPennyDropService.processCallback(nbfcResponseDTO);
            default:
                return false;
        }
    }


    public boolean invokeTopupUndoApproveService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        switch (lender) {
            case "TRILLIONLOANS":
                return tlTopupUndoApproveService.invokeTopupUndoApprove(lenderAssociationDetailsDto);
            default:
                return false;
        }
    }

    public boolean invokeTopupDataService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        switch (lender) {
            case "TRILLIONLOANS":
                return tlTopupDataService.invokeTopupData(lenderAssociationDetailsDto);
            default:
                return false;
        }
    }

    public boolean invokeAddChargeService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        switch (lender) {
            case "TRILLIONLOANS":
                return tlAddChargeService.invokeAddCharge(lenderAssociationDetailsDto);
            default:
                return false;
        }
    }

    public boolean invokeTopupApproveService(String lender, LenderAssociationDetailsRequestDto lenderAssociationDetailsDto) {
        switch (lender) {
            case "TRILLIONLOANS":
                return tlTopupApproveService.invokeTopupApprove(lenderAssociationDetailsDto);
            default:
                return false;
        }
    }
}
