package com.bharatpe.lending.loanV3.services.associationsV2.trillionloans.validations;

import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.request.trillionloans.TLNachMandateRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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

    public boolean isInvalidNachMandatePayload(TLNachMandateRequestDto tlNachMandateRequestDto) {
        return (ObjectUtils.isEmpty(tlNachMandateRequestDto)
                || ObjectUtils.isEmpty(tlNachMandateRequestDto.getUmrn()) || ObjectUtils.isEmpty(tlNachMandateRequestDto.getBankAccountHolderName())
                || ObjectUtils.isEmpty(tlNachMandateRequestDto.getBankName()) || ObjectUtils.isEmpty(tlNachMandateRequestDto.getBankAccountNumber())
                || ObjectUtils.isEmpty(tlNachMandateRequestDto.getIfsc()) || ObjectUtils.isEmpty(tlNachMandateRequestDto.getBankAccountType())
                || ObjectUtils.isEmpty(tlNachMandateRequestDto.getMandateRegistrationRequestedDate()) || ObjectUtils.isEmpty(tlNachMandateRequestDto.getPeriodStartDate())
                || ObjectUtils.isEmpty(tlNachMandateRequestDto.getPeriodEndDate()) || ObjectUtils.isEmpty(tlNachMandateRequestDto.getDebitTypeEnum())
                || ObjectUtils.isEmpty(tlNachMandateRequestDto.getDebitFrequencyEnum()) || ObjectUtils.isEmpty(tlNachMandateRequestDto.getAmount())
                || ObjectUtils.isEmpty(tlNachMandateRequestDto.getMode()) || ObjectUtils.isEmpty(tlNachMandateRequestDto.getLeadId())
        );
    }
}
