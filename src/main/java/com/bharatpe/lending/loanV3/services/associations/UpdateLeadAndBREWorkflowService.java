package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.wrapper.InvokeUpdateLeadAndBREWorkflowWrapperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class UpdateLeadAndBREWorkflowService implements ILenderAssociationService<Optional> {

    @Autowired
    @Lazy
    InvokeUpdateLeadAndBREWorkflowWrapperService updateLeadAndBREWorkflowWrapperService;

    @Override
    public Optional invoke(Long applicationId, Map<String, Object> args) {
        try {
            Map<String,String> request = new HashMap(){{
                put("application_id", applicationId.toString());
            }};
            log.info("invoking update lead api for {}", request);
            updateLeadAndBREWorkflowWrapperService.invokeUpdateLeadAndBREWorkflow(request, args);
        } catch (Exception e) {
            log.error("exception occurred while initiating update lead workflow for  {}, {}", applicationId, Arrays.asList(e.getStackTrace()));
        }
        return Optional.empty();
    }
}
