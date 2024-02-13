package com.bharatpe.lending.loanV3.services.associationsV2.wrapper;

import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Slf4j
@Service
public class KycCallbackWrapperService {
    @Autowired
    AssociationServiceUtil associationServiceUtil;

    public void kycCallback(NBFCResponseDTO nbfcResponseDTO) {
        try {
            log.info("KYC callback received {}", nbfcResponseDTO);
            associationServiceUtil.handleKycCallback(nbfcResponseDTO.getLender(), nbfcResponseDTO);
        } catch (Exception e) {
            log.info("Exception in consuming KYC callback of {} for applicationId {}  {} {}", nbfcResponseDTO.getLender(), nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }
}
