package com.bharatpe.lending.loanV3.services.associationsV2.piramal.validations;

import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.utils.KycUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Component
@Slf4j
public class CreateLeadPayloadValidationLayer {

    @Autowired
    KycUtils kycUtils;

    public boolean isInValidPayload(CKycResponseDto cKycResponseDto) {
        return ( ObjectUtils.isEmpty(kycUtils.getFirstName(cKycResponseDto)) ||
                ObjectUtils.isEmpty(kycUtils.getLastName(cKycResponseDto)) ||
                ObjectUtils.isEmpty(cKycResponseDto.getMobile()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getDob()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getCity()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getAadharNumber()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getAddress()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getPincode()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getState()) ||
                ObjectUtils.isEmpty(cKycResponseDto.getPanNumber())
        );
    }
}
