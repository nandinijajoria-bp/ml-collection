package com.bharatpe.lending.loanV2.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AddressDetails {
    private String pincode;
    private String area;
    private String city;
    private String state;
    private String address1;
    private String address2;
    private String landmark;
}
