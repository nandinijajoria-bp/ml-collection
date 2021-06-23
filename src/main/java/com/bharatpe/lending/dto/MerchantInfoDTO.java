package com.bharatpe.lending.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class MerchantInfoDTO {

    private List<Details> data;
    private Boolean status;

    @Setter
    @Getter
    @ToString
    public static class AddressDetail {
        private Double latitude;
        private Double longitude;
        private String address;
        private String pinCode;
        private String city;
        private String state;
        private String type;
        private String addressType;
    }

    @Setter
    @Getter
    @ToString
    public static class MerchantDetail {
        private String mobile;
        private String name;
        private String bussinessName;
        private String bussinessCategory;
    }

    @Setter
    @Getter
    @ToString
    public static class Details {
        private MerchantDetail merchantDetail;
        private List<AddressDetail> addressDetail;
    }
}
