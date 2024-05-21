package com.bharatpe.lending.loanV3.dto.request.muthoot;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MFUpdateLeadRequestDTO {

    private String customerID;
    private String program;
    private BasicDetail basicDetails;
    private List<Consent> consents;
    private MandateDetails mandateDetails;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BasicDetail {
        private PersonalDetail personalDetails;
        private BusinessDetail businessDetails;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PersonalDetail {
        private String pan;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BusinessDetail {
        private Address address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Consent {
        private String type;
        private String timestamp;
        private String body;
        private String ipAddress;
        private String url;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MandateDetails {
        private String accountNumber;
        private String accountType;
        private String bankName;
        private String accountHolderName;
        private String mandateType;
        private String ifsc;
        private Double mandateAmount;
        private String vendor;
        @NotBlank(message = "vendor doc id can not be blank")
        private String vendorDocID;
        @NotBlank(message = "npci txn id can not be blank")
        private String npciTxnID;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Address {
        private String businessAddressType;
        @NotBlank(message = "address line 1 can not be blank")
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String pincode;
        private String landmark;
        private Location location;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Location {
        private String latitude;
        private String longitude;
    }

}