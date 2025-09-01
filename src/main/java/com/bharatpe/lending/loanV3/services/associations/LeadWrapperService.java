package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.wrapper.InvokeLeadWrapperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class LeadWrapperService implements ILenderAssociationService {
    @Autowired
    InvokeLeadWrapperService invokeLeadWrapperService;

    @Override
    public Optional invoke(Long applicationId, Map args) {
        try {
            Map<String, String> request = new HashMap() {{
                put("application_id", applicationId.toString());
            }};
            invokeLeadWrapperService.invokeCreateLead(request, args);
            log.info("invoking createLead api for {}", request);
        } catch (Exception e) {
            log.error("exception occurred while initiating lead workflow for  {}, {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
        return Optional.empty();
    }
}
