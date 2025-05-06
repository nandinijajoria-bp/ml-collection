package com.bharatpe.lending.enums;

public enum ApplicationDocType {
    KEY_FACTS_STATEMENT_DETAILS,
    KEY_FACTS_STATEMENT_DOC,
    SANCTION_CUM_LOAN_AGREEMENT_DOC,
    ABFL_LETTERHEAD_HEADER,
    ABFL_LETTERHEAD_FOOTER,
    WELCOME_LETTER_DOC,
    HINDON_LETTERHEAD_HEADER,
    HINDON_LETTERHEAD_FOOTER,
    PIRAMAL_LETTERHEAD_FOOTER,
    DISBURSMENT_REQUEST_LETTER_DOC,
    LIQUILOANS_NBFC_FOOTER,
    AUTHORIZATION_LETTER_DOC,
    MUTHOOT_LETTERHEAD_FOOTER,
    CAPRI_LETTERHEAD_FOOTER,
    PAYU_LETTERHEAD_FOOTER,
    PAYU_MITC_DOC,
    PAYU_GTC_DOC,
    LOA_DOC,
    APPLICATION_FORM_DOC,
    OXYZO_LETTERHEAD_FOOTER,
    SMFG_LETTERHEAD_FOOTER;


    public static ApplicationDocType getFooterMapping(Lender lender) {
        switch (lender) {
            case ABFL:
                return ABFL_LETTERHEAD_FOOTER;
            case PIRAMAL:
                return PIRAMAL_LETTERHEAD_FOOTER;
            case LIQUILOANS_NBFC:
            case TRILLIONLOANS:
                return LIQUILOANS_NBFC_FOOTER;
            case MUTHOOT:
                return MUTHOOT_LETTERHEAD_FOOTER;
            case CAPRI:
                return CAPRI_LETTERHEAD_FOOTER;
            case PAYU:
                return PAYU_LETTERHEAD_FOOTER;
            case SMFG:
                return SMFG_LETTERHEAD_FOOTER;
        }
        return null;
    }

}
