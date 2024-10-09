package com.bharatpe.lending.loanV3.services.associationsV2.piramal.validations;

import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Component
public class DocUploadValidationLayer {
    public boolean isInValidPayload(CKycResponseDto cKycResponseDto) {
        return ObjectUtils.isEmpty(cKycResponseDto.getSelfieString());
    }
}
