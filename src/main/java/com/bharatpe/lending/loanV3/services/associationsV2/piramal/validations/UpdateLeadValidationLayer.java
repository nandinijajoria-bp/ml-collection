package com.bharatpe.lending.loanV3.services.associationsV2.piramal.validations;

import com.bharatpe.lending.loanV3.dto.piramal.CreateLeadRequestDTO;
import com.bharatpe.lending.loanV3.dto.piramal.NbfcRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.Objects;

@Component
@Slf4j
public class UpdateLeadValidationLayer {

    @Autowired
    ObjectMapper objectMapper;

    public boolean isInvalidPayload(NbfcRequestDto updateLeadRequestDto, long applicationId) {
        try{
            CreateLeadRequestDTO createLeadRequestDTO = objectMapper.readValue(objectMapper.writeValueAsString(updateLeadRequestDto.getPayload()), CreateLeadRequestDTO.class);
            if (ObjectUtils.isEmpty(createLeadRequestDTO.getLeadId())
                    || ObjectUtils.isEmpty(createLeadRequestDTO.getAdditionalInformation().getLoanSegment())
                    || ObjectUtils.isEmpty(createLeadRequestDTO.getAdditionalInformation().getLoanSegment())
                    || ObjectUtils.isEmpty(createLeadRequestDTO.getAdditionalInformation().getPincodeColor())
                    || ObjectUtils.isEmpty(createLeadRequestDTO.getAdditionalInformation().getMonthlyNFI())
                    || ObjectUtils.isEmpty(createLeadRequestDTO.getAdditionalInformation().getTotal60DaysTPV())
                    || ObjectUtils.isEmpty(createLeadRequestDTO.getApplicantsDetail().get(0).getBusinessInformation().getIndustry())
                    || ObjectUtils.isEmpty(createLeadRequestDTO.getApplicantsDetail().get(0).getBusinessInformation().getSubIndustry())
                    || ObjectUtils.isEmpty(createLeadRequestDTO.getApplicantsDetail().get(0).getBusinessInformation().getBusinessName())
                    || ObjectUtils.isEmpty(createLeadRequestDTO.getAuditTrailInformation().getAuditTrailList().get(0).getIpAddress()))
            {
                log.info("validation layer failed for update lead for application: {}", applicationId);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("exception in update lead during payload validation for application: {} {} {}", e.getMessage(), Arrays.asList(e.getStackTrace()), applicationId);
        }
        return true;
    }
}
