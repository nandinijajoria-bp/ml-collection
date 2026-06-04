package com.bharatpe.lending.loanV3.dto.sib;

import com.bharatpe.lending.loanV3.dto.request.sib.SibForeclosureRequest;
import com.bharatpe.lending.loanV3.dto.response.sib.SibForeclosureResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

public class SibForeclosureDtoJsonTest {

    @Test
    public void sibForeclosureRequest_serializesWithJsonPropertyNames() throws Exception {
        ObjectMapper om = new ObjectMapper();
        String json = om.writeValueAsString(SibForeclosureRequest.builder()
                .nposConfigId(3)
                .investorLoanId("LOAN-1")
                .build());
        JsonNode n = om.readTree(json);
        Assert.assertTrue(n.has("npos_config_id"));
        Assert.assertTrue(n.has("investor_loan_id"));
        Assert.assertEquals(3, n.get("npos_config_id").asInt());
        Assert.assertEquals("LOAN-1", n.get("investor_loan_id").asText());
    }

    @Test
    public void sibForeclosureResponse_deserializesForeclosureAmount() throws Exception {
        String json = "{\"data\":{\"foreclosure_amount\":42.5,\"account_status\":\"C\"}}";
        ObjectMapper om = new ObjectMapper();
        SibForeclosureResponse r = om.readValue(json, SibForeclosureResponse.class);
        Assert.assertNotNull(r.getData());
        Assert.assertEquals(42.5d, r.getData().getForeclosureAmount(), 0.0001);
    }
}
