package com.bharatpe.lending.loanV3.factory;

import com.bharatpe.lending.common.enums.LenderAssociationStages;
import com.bharatpe.lending.enums.Lender;
import org.springframework.stereotype.Component;

@Component
public class LenderAssociationStageFactoryV2 {
    public static LenderAssociationStages getNextStage(Lender lender, LenderAssociationStages stage) {
        switch (stage) {
            case INIT:
                switch (lender) {
                    case USFB:
                    case TRILLIONLOANS:
                        return LenderAssociationStages.KYC;
                    default:
                        return LenderAssociationStages.OLD_MODEL;
                }
            case KYC:
                switch (lender) {
                    case USFB:
                    case TRILLIONLOANS:
                        return LenderAssociationStages.BRE;
                    default:
                        return LenderAssociationStages.OLD_MODEL;
                }
            case BRE:
                switch (lender) {
                    case USFB:
                    case TRILLIONLOANS:
                        return LenderAssociationStages.ASSC_COMPLETED;
                    default:
                        return LenderAssociationStages.OLD_MODEL;
                }
            case ASSC_COMPLETED:
                switch (lender) {
                    case USFB:
                        return LenderAssociationStages.DOC_UPLOAD;
                    case TRILLIONLOANS:
                        return LenderAssociationStages.DIGI_SIGN;
                    default:
                        return LenderAssociationStages.OLD_MODEL;
                }
            case SANCTION_WRAPPER:
                switch (lender){
                    case TRILLIONLOANS:
                        return LenderAssociationStages.DRAWDOWN;
                    default:
                        return LenderAssociationStages.OLD_MODEL;
                }
            case DOC_UPLOAD:
                switch (lender) {
                    case USFB:
                        return LenderAssociationStages.DRAWDOWN;
                    case TRILLIONLOANS:
                        return LenderAssociationStages.SANCTION_WRAPPER;
                    default:
                        return LenderAssociationStages.OLD_MODEL;
                }
            case DRAWDOWN:
                switch (lender) {
                    case USFB:
                    case TRILLIONLOANS:
                        return LenderAssociationStages.COMPLETED;
                    default:
                        return LenderAssociationStages.OLD_MODEL;
                }

            case DIGI_SIGN:
                switch (lender) {
                    case TRILLIONLOANS:
                        return LenderAssociationStages.DOC_UPLOAD;
                    default:
                        return LenderAssociationStages.OLD_MODEL;
                }
            default:
                switch (lender) {
                    case USFB:
                    case TRILLIONLOANS:
                        return LenderAssociationStages.KYC;
                    default:
                        return LenderAssociationStages.OLD_MODEL;
                }
        }
    }

    public static Boolean autoInvokeNextStage(Lender lender, LenderAssociationStages stage) {
        switch (stage) {
            case INIT:
                switch (lender) {
                    case USFB:
                    case TRILLIONLOANS:
                        return Boolean.TRUE;
                    default:
                        return Boolean.FALSE;
                }
            case BRE:
                switch (lender) {
                    case USFB:
                    case TRILLIONLOANS:
                        return Boolean.FALSE;
                    default:
                        return Boolean.FALSE;
                }
            case KYC:
                switch (lender) {
                    case USFB:
                    case TRILLIONLOANS:
                        return Boolean.TRUE;
                    default:
                        return Boolean.FALSE;
                }
            case ASSC_COMPLETED:
                switch (lender) {
                    case USFB:
                    case TRILLIONLOANS:
                        return Boolean.TRUE;
                    default:
                        return Boolean.FALSE;
                }
            case DRAWDOWN:
                switch (lender) {
                    case USFB:
                    case TRILLIONLOANS:
                        return Boolean.FALSE;
                    default:
                        return Boolean.FALSE;
                }
            case DOC_UPLOAD:
                switch (lender) {
                    case USFB:
                    case TRILLIONLOANS:
                        return Boolean.TRUE;
                    default:
                        return Boolean.FALSE;
                }
            case SANCTION_WRAPPER:
                switch (lender) {
                    case TRILLIONLOANS:
                        return Boolean.FALSE;
                    default:
                        return Boolean.FALSE;
                }
            case DIGI_SIGN:
                switch (lender) {
                    case TRILLIONLOANS:
                        return Boolean.TRUE;
                    default:
                        return Boolean.FALSE;
                }
            default:
                return Boolean.FALSE;
        }
    }
}
