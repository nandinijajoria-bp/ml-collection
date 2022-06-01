package com.bharatpe.lending.service.merchant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class MerchantDetailsDto {

    BasicDetailsDto merchantDetail;
    BankDetailsDto bankDetail;
    List<VPADetailsDTO> vpaDetail;
    List<AddressDetailsDto> addressDetail;
    MerchantUserDto merchantUser;

}
