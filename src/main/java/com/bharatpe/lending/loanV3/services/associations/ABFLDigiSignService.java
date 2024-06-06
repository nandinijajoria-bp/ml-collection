package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.util.ConfigResolver;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.loanV3.dto.AbflDigiSignResponseDTO;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.dto.AbflDigiSignResponseDTO;
import com.bharatpe.lending.loanV3.services.associationsV2.AbflDigiSignService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ABFLDigiSignService implements ILenderAssociationService {
    @Autowired
    AbflDigiSignService abflDigiSignService;

    @Autowired
    LendingApplicationDao lendingApplicationDao;

    @Autowired
    ConfigResolver configResolver;

    @Override
    public AbflDigiSignResponseDTO invoke(Long applicationId, Map args) {
        try {
            MDC.put("requestId", UUID.randomUUID().toString());
            log.info("Received digisign upload request:{}", applicationId);

            Optional<LendingApplication> lendingApplication = lendingApplicationDao.findById(applicationId);
            if (!lendingApplication.isPresent()) {
                log.info("unable to initiate digisign as no application found for id {}", applicationId);
                return null;
            }
            log.info("DIGI sign: invoked via async api for application id {}", lendingApplication.get().getId());
            return abflDigiSignService.invokeDigiSign(lendingApplication.get().getId(), lendingApplication.get());
        } catch (Exception e) {
            log.error("exception occurred while initiating digiSign workflow for applicationId: {}", applicationId);
        }
        return null;
    }
}
