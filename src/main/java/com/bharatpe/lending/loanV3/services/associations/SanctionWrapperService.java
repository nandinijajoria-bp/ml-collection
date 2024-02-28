package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.wrapper.InvokeSanctionWrapperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class SanctionWrapperService implements ILenderAssociationService {

    @Autowired
    InvokeSanctionWrapperService invokeSanctionWrapperService;

    @Override
    public Optional invoke(Long applicationId, Map args) {
        try {
            Map<String, String> request = new HashMap() {{
                put("application_id", applicationId.toString());
            }};
            invokeSanctionWrapperService.invokeSanctionFlow(request, args);
            log.info("invoking sanction api for {}", request);
        } catch (Exception e) {
            log.error("exception occurred while initiating sanction workflow for  {}, {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
        return Optional.empty();
    }
}
