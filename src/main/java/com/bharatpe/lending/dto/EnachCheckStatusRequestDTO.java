package com.bharatpe.lending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.json.JSONPropertyName;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnachCheckStatusRequestDTO implements Serializable {

    @JsonProperty(value = "merchant")
    private MerchantInfo merchantInfo;

    @JsonProperty(value = "payment")
    private PaymentInfo paymentInfo;

    @JsonProperty(value = "transaction")
    private Transaction transaction;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(value = "consumer")
    private Consumer consumer;

    public MerchantInfo getMerchantInfo() {
        return merchantInfo;
    }

    public void setMerchantInfo(MerchantInfo merchantInfo) {
        this.merchantInfo = merchantInfo;
    }

    public PaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    public void setPaymentInfo(PaymentInfo paymentInfo) {
        this.paymentInfo = paymentInfo;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public String toString() {
        return "EnachCheckStatusRequestDTO {" +
                "Consumer = " + consumer + ",MerchantInfo=" + merchantInfo +
                ",Transaction=" + transaction + ",PaymentInfo=" + paymentInfo +
                "}";
    }

    public static class MerchantInfo {

        private String identifier = "L517110";

        public MerchantInfo() {
        }

        @Override
        public String toString() {
            return "MerchantInfo{" + "identifier=" + identifier + "}";
        }
    }

    public static class PaymentInfo {

        private Instruction instruction;

        public PaymentInfo(Instruction instruction) {
            this.instruction = instruction;
        }

        public Instruction getInstruction() {
            return instruction;
        }

        public void setInstruction(Instruction instruction) {
            this.instruction = instruction;
        }

        public PaymentInfo() {

        }

        public static class Instruction {

            @Override
            public String toString() {
                return "Instruction{" + "}";
            }
        }

        @Override
        public String toString() {
            return "PaymentInfo { "  + "}";
        }
    }

    public static class Transaction {

        private String deviceIdentifier = "S";
        private String type = "002";
        private String subtype = "002";
        private String currency = "INR";
        private String identifier = "1096";
        private String dateTime;
        private String requestType = "TSI";

        public Transaction(String dateTime) {
            this.dateTime = dateTime;
        }

        public void setDateTime(String dateTime) {
            this.dateTime = dateTime;
        }

        @Override
        public String toString() {
            return "Transaction{" + "deviceIdentifier=" + deviceIdentifier + ",type" + type + ",subtype" + subtype + "}";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Consumer {
        private String identifier;

        public Consumer(String identifier) {
            this.identifier = identifier;
        }

        @Override
        public String toString() {
            return "Consumer{" + "identifier=" + identifier + "}";
        }
    }
}
