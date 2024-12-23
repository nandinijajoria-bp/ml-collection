package com.bharatpe.lending.loanV3.services.associationsV2.muthoot.validations;

import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.request.muthoot.MFUpdateLeadRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Slf4j
@Service
public class LeadPayloadValidation {
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

    public boolean isInValidUpdateLeadPayload(MFUpdateLeadRequestDTO requestDTO) {
        log.info("MFUpdateLeadRequestDTO: {}", requestDTO);
        return (ObjectUtils.isEmpty(requestDTO)
                || ObjectUtils.isEmpty(requestDTO.getMandateDetails())
                || ObjectUtils.isEmpty(requestDTO.getMandateDetails().getNpciTxnID())
                || ObjectUtils.isEmpty(requestDTO.getMandateDetails().getVendorDocID())
                || ObjectUtils.isEmpty(requestDTO.getBasicDetails())
                || ObjectUtils.isEmpty(requestDTO.getBasicDetails().getBusinessDetails()))
                || ObjectUtils.isEmpty(requestDTO.getBasicDetails().getBusinessDetails().getAddress())
                || ObjectUtils.isEmpty(requestDTO.getBasicDetails().getBusinessDetails().getAddress().getLine1()
        );
    }
}
