package com.bharatpe.lending.loanV3.services.associationsV2.ugro.validations;

import com.bharatpe.lending.common.entity.LendingApplicationLenderDetails;
import com.bharatpe.lending.loanV3.config.UgroConfig;
import com.bharatpe.lending.loanV3.dto.CKycResponseDto;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroNachMandateRequest;
import com.bharatpe.lending.loanV3.dto.request.ugro.UgroPennyDropRequest;
import com.bharatpe.lending.loanV3.dto.response.ugro.UgroDisbursalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Slf4j
@Component
public class UgroPayloadValidation {

    @Autowired
    UgroConfig ugroConfig;

    public boolean isInvalidCreateLeadCKycData(CKycResponseDto cKycResponseDto) {
        return (ObjectUtils.isEmpty(cKycResponseDto)
                || (ObjectUtils.isEmpty(cKycResponseDto.getDob()) && ObjectUtils.isEmpty(cKycResponseDto.getPanDob()))
                || ObjectUtils.isEmpty(cKycResponseDto.getName())
                || ObjectUtils.isEmpty(cKycResponseDto.getMobile())
                || ObjectUtils.isEmpty(cKycResponseDto.getAadharNumber())
                || ObjectUtils.isEmpty(cKycResponseDto.getPanNumber())
                || ObjectUtils.isEmpty(cKycResponseDto.getAddress())
                || ObjectUtils.isEmpty(cKycResponseDto.getPincode())
        );
    }

    public boolean isInValidDocUploadPayload(CKycResponseDto cKycResponseDto) {
        return (ObjectUtils.isEmpty(cKycResponseDto)
                || ObjectUtils.isEmpty(cKycResponseDto.getSelfieString())
                || ObjectUtils.isEmpty(cKycResponseDto.getPoaString())
        );
    }

    public boolean isInvalidNachMandatePayload(UgroNachMandateRequest nachMandateRequest) {
        return (ObjectUtils.isEmpty(nachMandateRequest)
                || ObjectUtils.isEmpty(nachMandateRequest.getLeadId()) || ObjectUtils.isEmpty(nachMandateRequest.getNachMode())
                || ObjectUtils.isEmpty(nachMandateRequest.getNachVendor()) || ObjectUtils.isEmpty(nachMandateRequest.getStatus())
        );
    }

    public boolean isInvalidUpdateLeadPayload(UgroPennyDropRequest updateLead) {
        return (ObjectUtils.isEmpty(updateLead)
                || ObjectUtils.isEmpty(updateLead.getProfileData().getBankAccounts().get(0).getAccountNumber())
                || ObjectUtils.isEmpty(updateLead.getProfileData().getBankAccounts().get(0).getIfsc())
                || ObjectUtils.isEmpty(updateLead.getProfileData().getBankAccounts().get(0).getAccountNumber())
                || ObjectUtils.isEmpty(updateLead.getProfileData().getBankAccounts().get(0).getPurposeType())
        );
    }

    public boolean isInvalidSuccessDisbursalResponse(UgroDisbursalResponse disbursalResponse, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        return (ObjectUtils.isEmpty(disbursalResponse) || ObjectUtils.isEmpty(lendingApplicationLenderDetails)
                || ObjectUtils.isEmpty(lendingApplicationLenderDetails.getLeadId()) || ObjectUtils.isEmpty(lendingApplicationLenderDetails.getLan())
                || ObjectUtils.isEmpty(disbursalResponse.getEvents())
                || ObjectUtils.isEmpty(disbursalResponse.getEvents().get(0).getBankRefNo()) || ObjectUtils.isEmpty(disbursalResponse.getEvents().get(0).getDisbursalAmount())
                || ObjectUtils.isEmpty(disbursalResponse.getEvents().get(0).getDate())
                || ObjectUtils.isEmpty(disbursalResponse.getEvents().get(0).getType())
                || !ugroConfig.getDisbursalType().equalsIgnoreCase(disbursalResponse.getEvents().get(0).getType())
        );
    }

    public boolean isInvalidRejectedDisbursalResponse(UgroDisbursalResponse disbursalResponse, LendingApplicationLenderDetails lendingApplicationLenderDetails) {
        return (ObjectUtils.isEmpty(disbursalResponse) || ObjectUtils.isEmpty(lendingApplicationLenderDetails)
                || ObjectUtils.isEmpty(lendingApplicationLenderDetails.getLeadId()) || !ugroConfig.getRejectedResponse().equalsIgnoreCase(disbursalResponse.getStatus())
        );
    }
}
