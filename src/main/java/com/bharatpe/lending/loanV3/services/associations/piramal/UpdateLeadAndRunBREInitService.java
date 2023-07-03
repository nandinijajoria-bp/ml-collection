package com.bharatpe.lending.loanV3.services.associations.piramal;

import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.wrapper.UpdateLeadAndRiskDecisionWrapperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class UpdateLeadAndRunBREInitService implements ILenderAssociationService<Optional> {

    @Autowired
    UpdateLeadAndRiskDecisionWrapperService updateLeadAndRiskDecisionWrapperService;

    @Override
    public Optional invoke(Long applicationId, Map<String, Object> args) {
        try {
            Map<String,String> request = new HashMap(){{
                put("application_id", applicationId.toString());
            }};
            log.info("invoking update lead api for {}", request);
            updateLeadAndRiskDecisionWrapperService.invokeUpdateLeadAndRiskDecisionWorkflow(request, args);
        } catch (Exception e) {
            log.error("exception occurred while initiating update lead workflow for  {}", applicationId);
            // TODO: 09/03/23 add lender change workflow
        }
        return Optional.empty();
    }
}
