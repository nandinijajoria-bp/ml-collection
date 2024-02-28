package com.bharatpe.lending.loanV3.services.associationsV2.wrapper;

import com.bharatpe.lending.loanV3.dto.NBFCResponseDTO;
import com.bharatpe.lending.loanV3.services.associationsV2.AssociationServiceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Slf4j
@Service
public class DigitalSignCallbackWrapperService {
    @Autowired
    AssociationServiceUtil associationServiceUtil;

    public void digitalSignCallback(NBFCResponseDTO nbfcResponseDTO) {
        try {
            log.info("Digital Sign callback received {}", nbfcResponseDTO);
            associationServiceUtil.handleDigitalSignCallback(nbfcResponseDTO.getLender(), nbfcResponseDTO);
        } catch (Exception e) {
            log.info("Exception in consuming Digital Sig callback of {} for applicationId {}  {} {}", nbfcResponseDTO.getLender(), nbfcResponseDTO.getApplicationId(), e.getMessage(), Arrays.asList(e.getStackTrace()));
        }
    }
}
