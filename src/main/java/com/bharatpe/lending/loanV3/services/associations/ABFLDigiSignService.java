package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.lending.loanV3.dto.AbflDigiSignResponseDTO;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.AbflDigiSignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class ABFLDigiSignService implements ILenderAssociationService {
    @Autowired
    AbflDigiSignService abflDigiSignService;

    @Override
    public AbflDigiSignResponseDTO invoke(Long applicationId, Map args) {
        try {
            log.info("DIGI sign: invoked via async api for application id {}", applicationId);
            return abflDigiSignService.invokeDigiSign(applicationId);
        } catch (Exception e) {
            log.error("exception occurred while initiating digiSign workflow for  {}", applicationId);
        }
        return null;
    }
}