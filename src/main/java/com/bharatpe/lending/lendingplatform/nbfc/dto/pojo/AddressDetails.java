package com.bharatpe.lending.lendingplatform.nbfc.dto.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressDetails {
    private float latitude;
    private float longitude;
    private String address1;
    private String address2;
    private String address3;
    private String landmark;
    private long pincode;
    private String city;
    private String state;
    private String stateCode;
    private String area;
}
