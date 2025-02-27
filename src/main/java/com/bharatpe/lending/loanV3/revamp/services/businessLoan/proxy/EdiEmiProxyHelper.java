package com.bharatpe.lending.loanV3.revamp.services.businessLoan.proxy;

import com.bharatpe.lending.common.service.merchant.dto.BasicDetailsDto;
import com.bharatpe.lending.loanV3.utils.EmiUtils;
import com.bharatpe.lending.util.CommonUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.MapUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RequiredArgsConstructor
public abstract class EdiEmiProxyHelper<Parameter,Header,Body,Response> {

    @Value("${business.loan.api.host:https://merchant-lending-emi.bharatpe.co.in}")
    protected String host;

    protected final RestTemplate restTemplate;
    protected final CommonUtil commonUtil;
    protected final EmiUtils emiUtils;

    public abstract boolean isNotEdiRequest(Parameter parameter, BasicDetailsDto merchant, Header header, Body body);
    public abstract Response getResponse(Parameter parameter, BasicDetailsDto merchant, Header header, Body body);

    protected HttpHeaders getHeaders(Map<String,String> headers){
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("token", MapUtils.getString(headers, "token"));
        httpHeaders.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        httpHeaders.set("client", "LENDING");
        return httpHeaders;
    }
}
