package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class CustomerShopDetails {
    private AddressDetails addressDetails;
    private int pincode;
    private String businessName;
}
