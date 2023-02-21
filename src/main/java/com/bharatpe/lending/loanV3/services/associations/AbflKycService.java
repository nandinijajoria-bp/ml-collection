package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AbflKycService implements ILenderAssociationService<Optional> {

    @Autowired
    KafkaTemplate kafkaTemplate;

    @Autowired
    LendingApplicationDetailsDao lendingApplicationDetailsDao;

    @Override
    public Optional invoke(Long applicationId, Map<String, Object> args) {
        try {
            Map<String,String> request = new HashMap(){{
                put("application_id", applicationId.toString());
            }};
            kafkaTemplate.send("invoke_kyc", request);
            log.info("request pushed to kyc_invoke kafka {}", request);
        } catch (Exception e) {
            log.error("exception occurred while initiating kyc workflow for  {}", applicationId);
        }
        return Optional.empty();
    }
}
