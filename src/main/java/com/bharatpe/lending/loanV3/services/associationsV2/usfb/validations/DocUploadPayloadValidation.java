package com.bharatpe.lending.loanV3.services.associationsV2.usfb.validations;

import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Slf4j
@Service
public class DocUploadPayloadValidation {
    public boolean isInvalidPayload(CKycResponseDto cKycResponseDto) {
        return (ObjectUtils.isEmpty(cKycResponseDto)
               || ObjectUtils.isEmpty(cKycResponseDto.getPoaString())
               || ObjectUtils.isEmpty(cKycResponseDto.getSelfieString())
        );
    }
}
