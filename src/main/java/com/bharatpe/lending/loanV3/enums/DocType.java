package com.bharatpe.lending.loanV3.enums;

public enum DocType {
    SELFIE("KYC_DOCUMENT", "IDENTITY_PROOF", ".jpeg", "URL"),
    DIGILOCKER_AADHAAR_XML("KYC_DOCUMENT", "IDENTITY_PROOF", ".xml", "RAW_DATA"),
    LOAN_AGREEMENT("LOAN_DOCUMENT", "OTHER", ".pdf", "URL"),
    SANCTION_LETTER("LOAN_DOCUMENT", "OTHER", ".pdf", "URL"),
    KEY_FACT_STATEMENT("LOAN_DOCUMENT", "OTHER", ".pdf", "URL"),
    SHOP_PHOTO("CONSENTS_DOCUMENT", "OTHER", ".jpeg", "URL"),
    SHOP_STOCK("CONSENTS_DOCUMENT", "OTHER", ".jpeg", "URL"),
    KEY_FACT_STATEMENT_LOAN_AGREEMENT_MERGED("LOAN_DOCUMENT", "OTHER", ".pdf", "URL"),
    LOAN_AGREEMENT_NEW("LOAN_DOCUMENT", "OTHER", ".pdf", "URL"),
    KEY_FACT_STATEMENT_NEW("LOAN_DOCUMENT", "OTHER", ".pdf", "URL"),
    MITC("LOAN_DOCUMENT", "OTHER", ".pdf", "URL"),
    GTC("LOAN_DOCUMENT", "OTHER", ".pdf", "URL"),
    LOA("LOAN_DOCUMENT", "OTHER", ".pdf", "URL"),
    APPLICATION_FORM("LOAN_DOCUMENT", "OTHER", ".pdf", "URL"),
    LOAN_DOCS("LOAN_DOCUMENT", "OTHER", ".pdf", "URL"),
    BUSINESS_DOC("BUSINESS_DOCUMENT", "OTHER", ".pdf", "URL"),
    AUDIT_TRAIL_DOC("AUDIT_TRAIL_DOC", "OTHER", ".pdf", "URL");


    String category;
    String subCategory;
    String fileExtension;
    String contentType;

    DocType(String category, String subCategory, String fileExtension, String contentType) {
        this.category = category;
        this.subCategory = subCategory;
        this.fileExtension = fileExtension;
        this.contentType = contentType;
    }

    public String getCategory() {
        return category;
    }

    public String getSubCategory() {
        return subCategory;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFileExtension() {
        return fileExtension;
    }
}
