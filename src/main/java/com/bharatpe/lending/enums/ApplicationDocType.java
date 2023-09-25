package com.bharatpe.lending.enums;

import static com.bharatpe.lending.enums.Lender.*;

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
    DISBURSMENT_REQUEST_LETTER_DOC;


    public static ApplicationDocType getFooterMapping(Lender lender) {
        switch (lender) {
            case ABFL:
                return ABFL_LETTERHEAD_FOOTER;
            case PIRAMAL:
                return PIRAMAL_LETTERHEAD_FOOTER;
        }
        return null;
    }

}
