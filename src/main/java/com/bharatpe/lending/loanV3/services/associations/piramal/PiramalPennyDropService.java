package com.bharatpe.lending.loanV3.services.associations.piramal;

import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.piramal.impl.PiramalPennyDropServiceV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class PiramalPennyDropService implements ILenderAssociationService<Optional> {

    @Autowired
    @Lazy
    PiramalPennyDropServiceV2 piramalPennyDropServiceV2;

    @Override
    public Optional invoke(Long applicationId, Map<String, Object> args) {
        try {
            Map<String,String> request = new HashMap(){{
                put("application_id", applicationId.toString());
            }};
            piramalPennyDropServiceV2.invokePennyDrop(request);
            log.info("penny drop invoked for {}", request);
        } catch (Exception e) {
            log.error("exception occurred while initiating penny drop workflow for  {}", applicationId);
        }
        return Optional.empty();
    }
}
