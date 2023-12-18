package com.bharatpe.lending.loanV3.factory;

import com.bharatpe.lending.enums.Lender;
import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.loanV3.services.associations.ABFLDigiSignService;
import com.bharatpe.lending.loanV3.services.stages.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LenderAssociationStageFactory {

    @Autowired
    KycStageAssociationSvcFactory kycStageAssociationSvcFactory;

    @Autowired
    BreStageAssociationSvcFactory breStageAssociationSvcFactory;

    @Autowired
    OldModelAssociationSvcFactory oldModelAssociationSvcFactory;

    @Autowired
    SanctionWrapperStageAssociationSvcFactory sanctionWrapperStageAssociationSvcFactory;

    @Autowired
    DataUploadStageAssociationSvcFactory dataUploadStageAssociationSvcFactory;

    @Autowired
    ForeClosureAmtStageSvcFactory foreClosureAmtStageSvcFactory;

    @Autowired
    ReceiptStageSvcFactory receiptStageSvcFactory;

    @Autowired
    PushAuditSvcFactory pushAuditSvcFactory;

    @Autowired
    DigitalSignStageAssociationFactory digitalSignStageAssociationFactory;

    public LenderAssociationServiceFactory getStageAssociatedLenderService(String stage) {
        if (LenderAssociationStages.KYC.name().equalsIgnoreCase(stage)) {
            return  kycStageAssociationSvcFactory;
        } else if (LenderAssociationStages.BRE.name().equalsIgnoreCase(stage)) {
            return breStageAssociationSvcFactory;
        } else if (LenderAssociationStages.SANCTION_WRAPPER.name().equalsIgnoreCase(stage)) {
            return sanctionWrapperStageAssociationSvcFactory;
        } else if (LenderAssociationStages.DATA_UPLOAD.name().equalsIgnoreCase(stage)) {
            return dataUploadStageAssociationSvcFactory;
        } else if (LenderAssociationStages.FORECLOSURE_FETCH.name().equalsIgnoreCase(stage)) {
            return foreClosureAmtStageSvcFactory;
        } else if (LenderAssociationStages.RECEIPT.name().equalsIgnoreCase(stage)) {
            return receiptStageSvcFactory;
        } else if (LenderAssociationStages.PUSH_AUDIT.name().equalsIgnoreCase(stage)) {
            return pushAuditSvcFactory;
        } else if (LenderAssociationStages.DIGI_SIGN.name().equalsIgnoreCase(stage)) {
            return digitalSignStageAssociationFactory;
        }
        return oldModelAssociationSvcFactory;
    }

    public static LenderAssociationStages getNextStage(Lender lender, LenderAssociationStages stage) {
        switch (lender) {
            case ABFL:
                switch (stage) {
                    case INIT:
                        return LenderAssociationStages.BRE;
                    case BRE:
                        return LenderAssociationStages.KYC;
                    case KYC:
                        return LenderAssociationStages.ASSC_COMPLETED;
                    case ASSC_COMPLETED:
                        return LenderAssociationStages.SANCTION_WRAPPER;
                    case SANCTION_WRAPPER:
                        return LenderAssociationStages.DRAWDOWN;
                    case DRAWDOWN:
                        return LenderAssociationStages.COMPLETED;
                    default:
                        return LenderAssociationStages.BRE;
                }
                case PIRAMAL:
                switch (stage) {
                    case INIT:
                        return LenderAssociationStages.KYC;
                    case BRE:
                        return LenderAssociationStages.ASSC_COMPLETED;
                    case KYC:
                        return LenderAssociationStages.BRE;
                    case ASSC_COMPLETED:
                        return LenderAssociationStages.PUSH_AUDIT;
                    case PUSH_AUDIT:
                        return LenderAssociationStages.DRAWDOWN;
                    case DRAWDOWN:
                        return LenderAssociationStages.COMPLETED;
                    default:
                        return LenderAssociationStages.KYC;
                }
            default:
                return LenderAssociationStages.OLD_MODEL;
        }
    }

    public static Boolean autoInvokeNextStage(Lender lender, LenderAssociationStages stage) {
        switch (lender) {
            case ABFL:
                switch (stage) {
                    case INIT:
                        return Boolean.TRUE;
                    case BRE:
                        return Boolean.TRUE;
                    case KYC:
                        return Boolean.FALSE;
                    case ASSC_COMPLETED:
                        return Boolean.TRUE;
                    case SANCTION_WRAPPER:
                        return Boolean.FALSE;
                    case DRAWDOWN:
                        return Boolean.FALSE;
                    case DATA_UPLOAD:
                        return Boolean.FALSE;
                    default:
                        return Boolean.FALSE;
                }
            case PIRAMAL:
                switch (stage) {
                    case INIT:
                        return Boolean.TRUE;
                    case BRE:
                        return Boolean.FALSE;
                    case KYC:
                        return Boolean.TRUE;
                    case ASSC_COMPLETED:
                        return Boolean.TRUE;
                    case PUSH_AUDIT:
                        return Boolean.FALSE;
                    case DRAWDOWN:
                        return Boolean.FALSE;
                    case DATA_UPLOAD:
                        return Boolean.FALSE;
                    default:
                        return Boolean.FALSE;
                }
            default:
                return Boolean.FALSE;
        }
    }
}
