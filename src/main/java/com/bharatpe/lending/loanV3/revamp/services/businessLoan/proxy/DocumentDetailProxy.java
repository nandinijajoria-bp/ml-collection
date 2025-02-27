package com.bharatpe.lending.loanV3.revamp.services.businessLoan.proxy;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.dto.DocumentDetailsDto;
import com.bharatpe.lending.dto.EmiDocumentDetailDto;
import com.bharatpe.lending.loanV3.utils.EmiUtils;
import com.bharatpe.lending.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class DocumentDetailProxy extends EdiEmiProxyHelper<Map<String,String>, Map<String,String>, Map<String,String>, DocumentDetailsDto>{

    private final String apiPath="/api/v1/documents/urls";

    public DocumentDetailProxy(RestTemplate restTemplate, CommonUtil commonUtil, EmiUtils emiUtils) {
        super(restTemplate, commonUtil, emiUtils);
    }

    @Override
    public boolean isNotEdiRequest(Map<String, String> params, BasicDetailsDto merchant, Map<String, String> headers, Map<String, String> body) {
        if(!emiUtils.isEmiFlowEnabled()){
            return false;
        }
        String loanType = MapUtils.getString(params,"plan_type");
        return "emi".equalsIgnoreCase(loanType);
    }

    @Override
    public DocumentDetailsDto getResponse(Map<String, String> params, BasicDetailsDto merchant, Map<String, String> headers, Map<String, String> body) {
        String url = host + apiPath + "?";
        url += "applicationId="+MapUtils.getString(params, "application_id");
        HttpHeaders httpHeaders = getHeaders(headers);
        HttpEntity<?> httpEntity = new HttpEntity<>(httpHeaders);
        log.info("Making document detail proxy call to bl. url is: {} and headers are: {}", url, httpEntity);
        EmiDocumentDetailDto response = restTemplate.exchange(url, HttpMethod.GET, httpEntity, EmiDocumentDetailDto.class).getBody();
        log.info("received bl response for document detail, response is: {}", response);
        return response==null?null:response.getResult();
    }
}
