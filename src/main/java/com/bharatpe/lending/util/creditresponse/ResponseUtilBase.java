package com.bharatpe.lending.util.creditresponse;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ResponseUtilBase {
    List<Long> exemptMerchant = Arrays.asList(2368388L);

    protected Logger logger = LoggerFactory.getLogger(ResponseUtilBase.class);

    protected JsonNode response;
    protected String type;

    public void setResponse(JsonNode response) {
        this.response = response;
    }

    public void setType(String type) {
        this.type = type;
    }
}
