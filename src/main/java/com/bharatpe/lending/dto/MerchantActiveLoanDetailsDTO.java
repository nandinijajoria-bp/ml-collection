package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MerchantActiveLoanDetailsDTO {
    private MerchantKycDetails kycDetails;
    private MerchantShopDetails shopDetails;
    private double loanAmount;
    private int loanTenure;
    private Long merchantId;

    @Data
    @Builder
    public static class MerchantKycDetails {
        private String nameAsPerPan;
        private String panNumber;
        private String dob;
        private String gender;
        private String mobileNumber;
        private AddressDTO permanentAddress;
    }

    @Data
    @Builder
    public static class MerchantShopDetails {
        private String shopOwnerName;
        private String shopName;
        private String shopCategory;
        private String shopSubCategory;
        private AddressDTO shopAddress;
    }

    @Data
    @Builder
    public static class AddressDTO {
        private String city;
        private String address;
        private String state;
        private String pincode;
    }
}


