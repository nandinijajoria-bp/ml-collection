package com.bharatpe.lending.loanV3.dto.request.smfg;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SmfgAppPushRequest {
    String partnerapplicationid;
    String partnerid;
    String apiaction;
    String programtype;
    LeadDetails leaddetails;
    BasicDetails basicdetails;
    LoanDetails loandetails;
    AddressDetails currentaddressdetails;
    AddressDetails permanentaddressdetails;
    WorkDetails workdetails;
    AdditionalDetails additionaldetails;
    AdditionalInfo additionalinfo;
    MandateDetails mandatedetails;
    RepaymentDisbBankDetails repaymentdisbbankdetails;
    UdyamDetails udyamdetails;


    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LeadDetails {
        String firstname;
        String middlename;
        String lastname;
        String mobilenumber;
        String emailaddress;
        String producttype;
        Long currentpincode;
        String pep;
        String employmenttype;
        String callbackurl;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BasicDetails {
        String nationality;
        String dob;
        String pannumber;
        Integer monthlyincome;
        Integer famhouseholdinc;
        String consentmode;
        String consentdate;
        String kycmode;
        String gender;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoanDetails {
        Integer loanamount;
        Long tenure;
        Double roi;
        String loantype;
        String partnerscorecardscore;
        Integer processingfeewithgst;
        Integer stampdutywithgst;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AddressDetails {
        String address1;
        String address2;
        String address3;
        String addresstype;
        String pincode;
        String permanentaddressameas;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WorkDetails {
        String company;
        String officeaddress1;
        String officeaddress2;
        String officeaddress3;
        Long officepincode;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdditionalDetails {
        String fathersName;
        String purposeofloan;
        Double selfiematchscore;
        Double livelinessscore;
        Integer last3monthtpv;
        Long appvintage;
        Integer monthlyadjtpv;
        String riskgroup;
        String merchantcategory;
        String merchantsubcategory;
        Integer nfi;
        String ispanaadharlinked;
        String isaadharmatched;
        String ispanverified;
        Double pennydropnamematchper;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AdditionalInfo {
        String field;
    }


    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MandateDetails {
        Integer emiamount;
        String mandateregflag;
        String emifrequency;
        String mandatereferenceno;
        String emistartdate;
        String emienddate;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepaymentDisbBankDetails {
        String accountno;
        String ifsccode;
        String accountholdername;
        String bankname;
        String accounttype;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UdyamDetails {
        String pslflag;
        String urcn;
    }
}
