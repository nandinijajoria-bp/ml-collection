package com.bharatpe.lending.loanV3.services.associationsV2.creditsaison.validations;

import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Slf4j
@Service
public class CreditSaisonPayloadValidation {
    public boolean isInValidCreateClientPayload(CKycResponseDto cKycResponseDto) {
        return (ObjectUtils.isEmpty(cKycResponseDto)
           || ObjectUtils.isEmpty(cKycResponseDto.getPanNumber())
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
