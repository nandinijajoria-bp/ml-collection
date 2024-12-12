package com.bharatpe.lending.loanV3.config;

import com.bharatpe.lending.loanV3.enums.StateMapping;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ObjectUtils;

@Getter
@Setter
@Configuration
@NoArgsConstructor
@ConfigurationProperties(prefix = CreditSaisonConfig.PREFIX)
public class CreditSaisonConfig {
    public static final String PREFIX = "com.bharatpe.lender.creditsaison";
    private String lendingProduct = "LENDING";
    private String loanProduct = "BPT";
    private String partnerId = "BP";
    private String adharXMLType = "DIGILOCKER";
    private String applicantType = "LinkedIndividual";
    private String salutation = "NA";
    private String country = "IN";
    private String fathersName = "X";
    private String contactTypePhone = "phone";
    private String contactTypeCodeMobile = "MOBILE";
    private String contactKYCTypePan = "panCard";
    private String kycType = "OKYC";
    private String source = "colending";
    private String loanType = "personal";
    private Integer priority5 = 5;
    private Integer priority4 = 4;
    private String contactPhotoType = "photo";
    private String contactAdharType = "aadhaar";
    private String consentMode = "clickwrap";
    private String consentChannel = "app";
    private String consentForWhatsapp = "whatsapp";
    private String consentForKFS = "kfs";
    private String consentForDataSharing = "data sharing";
    private String consentForAdhar = "aadhar";
    private String consentForSelfie = "selfie";
    private String consentForHHI = "hhi";
    private String consentForNSDL = "nsdl";
    private String consentForBureau = "bureau";
    private String currentAddressType = "CURRES";
    private String permanentAddressType = "PER";
    private String lenderTimeFormat = "yyyy-MM-dd'T'HH:mm:ss";
    private String metaDataKey = "ip";
    private String docTypeSanctionWrapper = "Insurance Policy";
    private String docTypeLoanAgreement = "Loan Agreement";
    private String docTypeScheduleLetter = "Schedule Letter";

    private String countryCode = "+91";

    private String notifyTrue = "TRUE";


    private String syncSuccessStatus = "SUCCESS";
    private String syncInProgressStatus = "IN_PROGRESS";
    private String pennyDropSyncInProgressStatus = "request in progress";
    private String pennyDropSyncAlreadyValidatedStatus = "Already validated the given account number";
    private String syncDocMessage = "Request is being successfully processed";
    private String employeeStatus = "self employed";
    private String businessType = "PROPRIETORSHIP";
    private String businessAddressOwnerShip = "SELF_OWNED";
    private Double businessMonthlyIncome = 100000D;
    private String businessAddressType = "Office";
    private String businessAddressCountry = "Office";
    private String businessApplicantProfile = "VENDOR";
    private String businessApplicantIndustry = "Automobile";
    private String businessApplicantDrog = "DROG";
    private String defaultBusinessCategory = "Others";
    private String bankTypeSaving = "savings";
    private String bankTypeCurrent = "current";
    private String nbfcCreditsaisonForeclosureTopic = "credit-saison-foreclose-loan";
    private String foreclosureTag = "FORECLOSURE";
    private String foreclosureModeOfPay = "ONLINE";
    private Double maxIRR=30.0;
    private Double maxApr=45.0;


    private Double income = 100000D;

    private Integer rolloutPercent = 1;




    public String getGender(String gender) {
        if(!ObjectUtils.isEmpty(gender)) {
            if("male".equalsIgnoreCase(gender)) {
                return "M";
            }
            return "F";
        }
        return "T";
    }

    public String getState(String state) {
        switch (StateMapping.getStateEnum(state)){
            case UK:
                return "UT";
            case TS:
                return "TL";
            case BR:
                return "BH";
            default:
                return StateMapping.getStateEnum(state).name();
        }
    }

}
