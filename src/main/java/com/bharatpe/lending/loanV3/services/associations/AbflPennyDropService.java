package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.AbflBreServiceV2;
import com.bharatpe.lending.loanV3.services.associationsV2.AbflPennyDropServiceV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AbflPennyDropService implements ILenderAssociationService<Optional> {

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Autowired
    @Lazy
    AbflPennyDropServiceV2 abflPennyDropServiceV2;

    @Override
    public Optional invoke(Long applicationId, Map<String, Object> args) {
        try {
            Map<String,String> request = new HashMap(){{
                put("application_id", applicationId.toString());
            }};
            abflPennyDropServiceV2.invokePennyDrop(request);
            log.info("bre invoked via async api for {}", request);
        } catch (Exception e) {
            log.error("exception occurred while initiating bre workflow for  {}", applicationId);
        }
        return Optional.empty();
    }
}
