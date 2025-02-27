package com.bharatpe.lending.loanV3.revamp.services.businessLoan.proxy;

import com.bharatpe.common.entities.LendingApplication;
import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dao.LendingApplicationDao;
import com.bharatpe.lending.dto.ENachIntitiationResponseDTO;
import com.bharatpe.lending.dto.ENachSubmitRequestDTO;
import com.bharatpe.lending.dto.EmiEnachInitiateResponse;
import com.bharatpe.lending.loanV3.utils.EmiUtils;
import com.bharatpe.lending.util.CommonUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Setter
@Service
public class NachSubmitProxy extends EdiEmiProxyHelper<Map<String,String>, Map<String,String>, ENachSubmitRequestDTO, ENachIntitiationResponseDTO>{

    @Value("${pre.nach.application.status:pending_verification,draft}")
    private List<String> preNachStatusList;

    private final String apiPath="/api/v1/enach/submit";

    private final LendingApplicationDao lendingApplicationDao;

    public NachSubmitProxy(RestTemplate restTemplate, CommonUtil commonUtil, EmiUtils emiUtils, LendingApplicationDao lendingApplicationDao) {
        super(restTemplate, commonUtil, emiUtils);
        this.lendingApplicationDao = lendingApplicationDao;
    }

    @Override
    public boolean isNotEdiRequest(Map<String, String> parameter, BasicDetailsDto merchant,
                                   Map<String, String> headers, ENachSubmitRequestDTO body) {
        if(!emiUtils.isEmiFlowEnabled()){
            return false;
        }
        LendingApplication lendingApplication =lendingApplicationDao.findTop1ByMerchantIdOrderByIdDesc(merchant.getId());
        if(lendingApplication==null || lendingApplication.getStatus() == null){
            return true;
        }
        String status = lendingApplication.getStatus().toLowerCase();
        return !preNachStatusList.contains(status);
    }

    @Override
    public ENachIntitiationResponseDTO getResponse(Map<String, String> parameter, BasicDetailsDto merchant,
                                                   Map<String, String> headers, ENachSubmitRequestDTO eNachSubmitRequestDTO) {
        String url = host + apiPath;
        HttpHeaders httpHeaders = getHeaders(headers);
        HttpEntity<?> httpEntity = new HttpEntity<>(eNachSubmitRequestDTO, httpHeaders);
        log.info("Making enach submit proxy call to bl. url is: {} and request are: {}", url, httpEntity);
        EmiEnachInitiateResponse resposne = restTemplate.exchange(url, HttpMethod.POST, httpEntity, EmiEnachInitiateResponse.class).getBody();
        log.info("received bl response for enach submit, response is: {}", resposne);
        return resposne==null ? null : resposne.getResult();
    }
}
