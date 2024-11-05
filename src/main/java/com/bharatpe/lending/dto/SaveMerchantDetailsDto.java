package com.bharatpe.lending.dto;

import com.bharatpe.lending.loanV2.dto.AdditionalDetails;
import com.bharatpe.lending.loanV2.dto.AddressDetails;
import com.bharatpe.lending.loanV2.dto.ProfessionalDetails;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveMerchantDetailsDto {

    private AddressDetails addressDetails;
    private ProfessionalDetails professionalDetails;
    private AdditionalDetails additionalDetails;
    private String businessName;
    private Boolean currentAddressSameAsPermanentAddress;

    @Override
    public String toString() {
        return "SaveMerchantDetailsDto{" +
                "addressDetails=" + addressDetails +
                ", professionalDetails=" + professionalDetails +
                ", additionalDetails=" + additionalDetails +
                ", businessName='" + businessName + '\'' +
                ", currentAddressSameAsPermanentAddress=" + currentAddressSameAsPermanentAddress +
                '}';
    }
}