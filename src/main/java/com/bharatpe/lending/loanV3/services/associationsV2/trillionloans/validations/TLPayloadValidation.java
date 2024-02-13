package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.validations;

import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Slf4j
@Component
public class TLPayloadValidation {
    public boolean isInValidCreateClientPayload(CKycResponseDto cKycResponseDto) {
        return (ObjectUtils.isEmpty(cKycResponseDto)
                || ObjectUtils.isEmpty(cKycResponseDto.getDob())
                || ObjectUtils.isEmpty(cKycResponseDto.getName())
                || ObjectUtils.isEmpty(cKycResponseDto.getMobile())
                || ObjectUtils.isEmpty(cKycResponseDto.getAadharNumber())
                || ObjectUtils.isEmpty(cKycResponseDto.getPanNumber())
                || ObjectUtils.isEmpty(cKycResponseDto.getAddress())
                || ObjectUtils.isEmpty(cKycResponseDto.getPincode())
                || ObjectUtils.isEmpty(cKycResponseDto.getGender())
        );
    }

    public boolean isInvalidCreateLeadPayload() {
        return false;
    }

    public boolean isInValidDocUploadPayload(CKycResponseDto cKycResponseDto) {
        return (ObjectUtils.isEmpty(cKycResponseDto)
                || ObjectUtils.isEmpty(cKycResponseDto.getSelfieString())
                || ObjectUtils.isEmpty(cKycResponseDto.getPoaString())
        );
    }
}
