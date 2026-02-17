package com.bharatpe.lending.loanV3.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = UgroConfig.PREFIX)
public class UgroConfig {
    public static final String PREFIX = "ugro";

    private Integer rolloutPercentage = 1;
    private String product = "TERM_LOAN::TL_BHARATPE_EDI";
    private String loanRequestPurpose = "Other";
    private String employmentType = "SELF_EMPLOYED";
    private Integer httpReadTimeout = 30000;
    private String typeOfOrganization = "Proprietory";
    private String businessSector = "Services";
    private Integer nachPlusDays = 14600;
    private String nachStatus = "ACTIVE";
    private String purposeType = "DISBURSAL";
    private String foreclosureIntent = "FORECLOSURE";
    private String successResponse = "SUCCESS";
    private String pendingResponse = "PENDING";
    private String closedResponse = "CLOSED";
    private String rejectedResponse = "REJECTED";
    private String disbursalType = "EventDisbursement";
    private String leadExpiryResponse = "No active lead found";

    private String foreclosureTopic = "ugro-foreclose-loan";

    private String aadhaarEnachMode = "AADHAAR";
    private String nbDcEnachMode = "DEBIT";
    private String udyamRedirectionUrl;

    private double minAmount = 15000;
    private double maxAmount = 500000;
    private double maxIrr = 38;

    private String corporateName = "UGRO Capital Limited";
    private String businessAddress = "UGRO Capital, 4th Floor, Tower 3, Equinox Business Park, Lbs Road, Kurla, Mumbai - 400070";
    private String contactName = "Mr. Satyabrata Mohapatra";
    private String contactEmail = "grievance@ugrocapital.com";
    private String contactNumber = "022-68269135";
    private String grievanceTIme = "9.30 am to 6 pm, Monday to Friday";

    private String defaultBsrCode = "74101";
    private Integer foreclosureDetailsTimeoutThreshold = 20000;

    public String getBsrCode(String key) {
        Map<String, String> bsrCodeMap = new HashMap<>();
        bsrCodeMap.put("Dairy_Fresh_Products", "15201");
        bsrCodeMap.put("Automobiles and Vehicles", "50001");
        bsrCodeMap.put("Automobiles", "50001");
        bsrCodeMap.put("Fuel", "50005");
        bsrCodeMap.put("Fuel_&_Gas", "50005");
        bsrCodeMap.put("Food_&_Beverages", "51204");
        bsrCodeMap.put("Food_and_Drink", "51204");
        bsrCodeMap.put("Food & Beverages", "51204");
        bsrCodeMap.put("Clothing_Accessories", "51303");
        bsrCodeMap.put("Electronics_&_Durables", "51303");
        bsrCodeMap.put("Retail Outlet", "52101");
        bsrCodeMap.put("Retail_Outlet", "52101");
        bsrCodeMap.put("Retail_Non_Essential_Services", "52101");
        bsrCodeMap.put("Retail_Essential_Services", "52102");
        bsrCodeMap.put("Travels,Transport & Hospitality", "55301");
        bsrCodeMap.put("Travel_Hospitality", "55301");
        bsrCodeMap.put("Near_Bus_Railway_Station", "63011");
        bsrCodeMap.put("Transportation", "63011");
        bsrCodeMap.put("Hospitals & Healthcare", "75001");
        bsrCodeMap.put("Medical_Health_Care", "75001");
        bsrCodeMap.put("Education_Recreation", "75001");
        bsrCodeMap.put("Education & Professional Classes", "75001");
        bsrCodeMap.put("Education", "80001");
        bsrCodeMap.put("Entertainment", "92109");
        bsrCodeMap.put("Beauty_Wellness", "93002");
        bsrCodeMap.put("Beauty and Wellness", "93002");
        return bsrCodeMap.getOrDefault(key, defaultBsrCode);
    }
}
