package com.bharatpe.lending.loanV3.services.associationsV2;

import com.bharatpe.lending.loanV3.consumer.SancWrapperRequestKafka;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Map;

@Slf4j
@Service
public class AbflSanctionServiceV2 {

    @Autowired
    SancWrapperRequestKafka sancWrapperRequestKafka;

    @Autowired
    ObjectMapper objectMapper;

    @Async
    @Transactional
    public void invokeSanctionViaAsyncApi(Map<String, String> request) {
        try {
            sancWrapperRequestKafka.sanctionRequestInvoke(objectMapper.writeValueAsString(request));
        } catch (Exception e) {
            log.error("exception occurred while initiating sanction v2 workflow for  {}", request);
        }
    }

}
