package com.bharatpe.lending.loanV3.services.associations;

import com.bharatpe.lending.common.dao.LendingApplicationDetailsDao;
import com.bharatpe.lending.loanV3.interfaces.ILenderAssociationService;
import com.bharatpe.lending.loanV3.services.associationsV2.AbflKycServiceV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${abfl.kafka.enabled:false}")
    private Boolean kafkaEnabled;

    @Autowired
    AbflKycServiceV2 abflKycServiceV2;

    @Override
    public Optional invoke(Long applicationId, Map<String, Object> args) {
        try {
            Map<String,String> request = new HashMap(){{
                put("application_id", applicationId.toString());
            }};
            if (kafkaEnabled) {
                kafkaTemplate.send("invoke_kyc", request);
                log.info("request pushed to kyc_invoke kafka {}", request);
            } else {
                abflKycServiceV2.invokeKycViaAsyncApi(request);
                log.info("kyc invoked via async api for {}", request);
            }
        } catch (Exception e) {
            log.error("exception occurred while initiating kyc workflow for  {}", applicationId);
        }
        return Optional.empty();
    }
}
