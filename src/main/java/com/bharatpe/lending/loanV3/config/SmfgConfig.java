package com.bharatpe.lending.loanV3.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@Configuration
@ConfigurationProperties(prefix = SmfgConfig.PREFIX)
public class SmfgConfig {

    public static final String PREFIX = "smfg";

    private String partnerId = "BHARATPE";
    private String programType = "1";
    private String appPushApiAction = "236698";
    private String dataPushApiAction = "236699";
    private String productType = "PL";
    private String pep = "258548";
    private String employmentType = "200564";
    private String callbackUrl = "https://bharatpe.in";
    private String nationality = "234365";
    private String consentMode = "OTP";
    private String kycMode = "258550";
    private Integer famHouseHoldingInclusive = 1;
    private String maleGender = "1715";
    private String femaleGender = "107";
    private String freshLoanType = "236695";
    private String regularEtcLoanType = "259599";
    private String repeatLoanType = "259600";
    private Integer stampDutyWithGst = 0;
    private String purposeOfLoan = "236844";
    private String currentAddressType = "20068";
    private String permanentAddressType = "20069";
    private String NegativeMandateFlag = "N";
    private String PositiveMandateFlag = "Y";
    private String currentAccountType = "20911";
    private String savingAccountType = "20912";
    private String dailyInstallmentFrequency = "230384";
    private String aadhaarDocType = "1";
    private String selfieDocType = "639";
    private String udyamDocType = "753";
    private String auditTrailDocType = "780";
    private Double maxApr = 36D;
    private Integer rolloutPercentage = 1;
    private String pslFlagTrue = "PSL_FLAG_TRUE";
    private String pslFlag = "Y";
    private Integer nachPlusDays = 10950;
    private Double selfieMatchPerThreshold = 70D;
    private Double faceLivelinessPerThreshold = 0.7;
    private Double benePanNameMatchPerThreshold = 0.6;
    private String foreclosureTopic = "smfg-loan-receipt";
    private Double shopInferredDistanceThreshold = 2500D;
    private Double maxProcessingFee = 6.0D;

    //LMS
    private String LmsAppName;
    private String LmsAppPassword;
    private String LmsStaticIpAddress = "10.1.1.3";
    private String LmsDeviceRpsId = "REPAYMENT_SCHEDULE";
    private String LmsGetForeclosureDeviceId = "PKG_RECEIPT_API";
    private String LmsMakePaymentDeviceId = "PKG_RECEIPT_API";
    private String LmsLongitude = "77.3877269";
    private String LmsLatitude = "28.6127356";
    private String foreclosureTowards = "FORECLOSURE";
    private Long onlinePaymentType = 1000000009L;
}
