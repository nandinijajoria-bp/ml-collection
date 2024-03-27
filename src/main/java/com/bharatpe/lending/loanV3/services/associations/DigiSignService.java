package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.wrapper.InvokeDigitalSignWrapperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class DigiSignService implements ILenderAssociationService<Optional> {

    @Lazy
    @Autowired
    InvokeDigitalSignWrapperService invokeDigitalSignWrapperService;

    @Override
    public Optional invoke(Long applicationId, Map args) {
        try {
            Map<String, String> request = new HashMap() {{
                put("application_id", applicationId.toString());
            }};
            log.info("digi Sign invoked via async api for {}", request);
            invokeDigitalSignWrapperService.invokeDigitalSign(applicationId, UUID.randomUUID().toString());
            log.info("Successfully invoked digital sign for application id : " + applicationId);
        } catch (Exception e) {
            log.error("exception occurred while initiating digiSign workflow for  {}", applicationId);
        }
        return Optional.empty();
    }
}
