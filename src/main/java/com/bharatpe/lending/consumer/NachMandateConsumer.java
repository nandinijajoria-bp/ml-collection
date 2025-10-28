package com.bharatpe.lending.consumer;

import com.bharatpe.lending.common.Constants.AutoPayStatusEnum;
import com.bharatpe.lending.common.dao.AutoPayUPIDao;
import com.bharatpe.lending.common.entity.AutoPayUPI;
import com.bharatpe.lending.constant.LendingConstants;
import com.bharatpe.lending.dao.NachMandateRevokeRequestDao;
import com.bharatpe.lending.entity.NachMandateRevokeRequest;
import com.bharatpe.lending.enums.CleverTapEvents;
import com.bharatpe.lending.loanV3.revamp.enums.NachStatus;
import com.bharatpe.lending.service.CleverTapEventService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class NachMandateConsumer {

    @Autowired
    private NachMandateRevokeRequestDao nachMandateRevokeRequestDao;
    @Autowired
    private CleverTapEventService cleverTapEventService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AutoPayUPIDao autoPayUPIDao;

    @KafkaListener(
            topics = "kafka.nach.mandate.lending",
            containerFactory = "ConfluentKafkaListenerContainer",
            autoStartup = "${kafka.confluent.consumer:false}")
    public void updateCancelNachMandateStatus(String data){
        log.info("Update status for mandate revoke {}", data);
        try{
            Map<String, Object> map = objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {});
            Long applicationId = MapUtils.getLong(map, LendingConstants.OWNER_ID);
            Long merchantId = MapUtils.getLong(map, LendingConstants.MERCHANT_ID);
            String mandateId = MapUtils.getString(map, LendingConstants.MANDATE_ID);
            String mandateNachStatus = MapUtils.getString(map, LendingConstants.MANDATE_NACH_STATUS);
            if (Objects.isNull(merchantId) || Objects.isNull(applicationId)){
                log.info("Invalid request to update mandate revoke request:{}", map);
                return;
            }
            String nachStatus = map.containsKey("nach_status")? String.valueOf(map.get("nach_status")):null;
            String status = map.containsKey("status")?String.valueOf(map.get("status")):null;
            if ("CANCELLED".equalsIgnoreCase(nachStatus) && "CANCELLED".equalsIgnoreCase(status)){
                NachMandateRevokeRequest nachMandateRevokeRequest = nachMandateRevokeRequestDao
                        .findTop1ByMerchantIdAndAndApplicationIdAndStatusOrderByIdDesc(merchantId, applicationId,"INIT");
                if(ObjectUtils.isEmpty(nachMandateRevokeRequest)){
                    log.info("no nach mandate request found for merchant:{}", merchantId);
                    return;
                }
                HashMap<String, Object> cleverTapEvtData = new HashMap<String, Object>() {{
                    put("applicationId", applicationId);
                }};
                cleverTapEventService.sendClevertapEvent(CleverTapEvents.NACH_CANCELLATION_SUCCESS.name(), cleverTapEvtData, merchantId.toString());
                nachMandateRevokeRequest.setStatus("SUCCESS");
                nachMandateRevokeRequestDao.save(nachMandateRevokeRequest);
                return;
            }
            updateAutoPayTable(applicationId, mandateId, status, mandateNachStatus);
        } catch (Exception ex){
            log.info("Exception in updating nach mandate revoke request status {}, {}", ex.getMessage(), Arrays.asList(ex.getStackTrace()));
            return;
            
        }
    }
    void updateAutoPayTable(Long applicationId, String mandateId, String status, String mandateNachStatus){
        log.info("Handling nach callback for application: {}, mandate: {}. status and nachStatus are: {}, {}",
                applicationId, mandateId, status, mandateNachStatus);
        AutoPayUPI autoPayUPI = autoPayUPIDao.findByApplicationIdAndMandateId(applicationId, mandateId);
        if(Objects.isNull(autoPayUPI)){
            log.info("entry not found in autopayupi table for application: {} and mandateId: {}", applicationId, mandateId);
            return;
        }
        if(!AutoPayStatusEnum.PENDING.equals(autoPayUPI.getStatus())){
            log.info("autopayupi entry is already in terminal state");
            return;
        }
        AutoPayStatusEnum finalStatus = null;
        if(NachStatus.APPROVED.name().equalsIgnoreCase(status) && NachStatus.APPROVED.name().equalsIgnoreCase(mandateNachStatus)){
            finalStatus = AutoPayStatusEnum.ACTIVE;
        }else if (NachStatus.REJECTED.name().equalsIgnoreCase(status)){
            finalStatus = AutoPayStatusEnum.FAILED;
        }else if(NachStatus.CANCELLED.name().equals(status)){
            finalStatus = AutoPayStatusEnum.CANCELLED;
        }
        if(finalStatus==null){
            log.info("got invalid status and nachStatus combination, not updating autopayupi table");
            return;
        }
        autoPayUPI.setStatus(finalStatus);
        autoPayUPIDao.save(autoPayUPI);
        log.info("updated autopayupi table for application: {} and mandateId: {} with status: {}",
                applicationId, mandateId, finalStatus);
    }

}