package com.bharatpe.lending.loanV3.services.associationsV2.usfb.validations;

import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Slf4j
@Service
public class CreateLeadPayloadValidation {
    public boolean isInValidPayload(CKycResponseDto cKycResponseDto) {
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
}
