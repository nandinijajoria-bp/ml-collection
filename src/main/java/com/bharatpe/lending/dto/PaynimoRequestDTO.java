package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaynimoRequestDTO {
    private Map<String, String> merchant;
    private Map<String, Object> payment;
    private Map<String, Object> transaction;
    private Map<String, Object> consumer;

    public PaynimoRequestDTO() {
        this.merchant = new HashMap<String, String>() {{
            put("identifier", "L517110");
        }};
        this.payment = new HashMap<String, Object>() {{
            put("instruction", new HashMap<>());
        }};
        this.transaction = new HashMap<String, Object>() {{
            put("deviceIdentifier", "S");
            put("type", "002");
            put("currency", "INR");
            put("subType", "002");
            put("requestType", "TSI");
            put("identifier", "1096");
        }};
        this.consumer = new HashMap<>();
    }

    public Map<String, String> getMerchant() {
        return merchant;
    }

    public void setMerchant(Map<String, String> merchant) {
        this.merchant = merchant;
    }

    public Map<String, Object> getPayment() {
        return payment;
    }

    public void setPayment(Map<String, Object> payment) {
        this.payment = payment;
    }

    public Map<String, Object> getTransaction() {
        return transaction;
    }

    public void setTransaction(Map<String, Object> transaction) {
        this.transaction = transaction;
    }

    public Map<String, Object> getConsumer() {
        return consumer;
    }

    public void setConsumer(Map<String, Object> consumer) {
        this.consumer = consumer;
    }

    @Override
    public String toString() {
        return "PaynimoRequestDTO{" +
                "merchant=" + merchant +
                ", payment=" + payment +
                ", transaction=" + transaction +
                ", consumer=" + consumer +
                '}';
    }
}

