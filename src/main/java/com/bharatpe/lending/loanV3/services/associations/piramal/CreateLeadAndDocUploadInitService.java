package com.bharatpe.lending.loanV3.services.associations.piramal;

import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.wrapper.InvokeCreateLeadAndDocUploadWraperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class CreateLeadAndDocUploadInitService implements ILenderAssociationService<Optional> {

    @Autowired
    @Lazy
    InvokeCreateLeadAndDocUploadWraperService invokeCreateLeadAndDocUploadWraperService;

    @Override
    public Optional invoke(Long applicationId, Map<String, Object> args) {
        try {
            Map<String,String> request = new HashMap(){{
                put("application_id", applicationId.toString());
            }};
            invokeCreateLeadAndDocUploadWraperService.invokeCreateLeadAndDocUpload(request, args);
            log.info("invoking create lead api  via async api for {}", request);
        } catch (Exception e) {
            log.error("exception occurred while initiating create lead workflow for  {}", applicationId);
            // TODO: 09/03/23 add lender change workflow
        }
        return Optional.empty();
    }
}
