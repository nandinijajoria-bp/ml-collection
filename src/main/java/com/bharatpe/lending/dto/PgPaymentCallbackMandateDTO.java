//package com.bharatpe.lending.dto;
//
//import lombok.Data;
//
//import java.util.Date;
//import java.util.List;
//
//@Data
//public class PgPaymentCallbackMandateDTO {
//    private Double orderAmount;
//    private Double paymentAmount;
//    private String paymentRefId;
//    private String beneficiaryName;
//    private String paymentStatus;
//    private String currency;
//    private String orderId;
//    private String paymentURI;
//    private String redirectURI;
//    private String checkoutType;
//    private List<Payments> payments;
//    private Mandate mandate;
//
//    @Data
//    public static class Payments {
//        private Double amount;
//        private String mode;
//        private String status;
//        private Date completedAt;
//        private String refId;
//        private String pgReferenceId;
//        private Double finalAmount;
//        private String breakupType;
//        private String finalGateway;
//        private String accountType;
//        private String pgOrderId;
//        private String terminalOrderId;
//    }
//
//
//    @Data
//    public static class Mandate {
//        private String type;
//        private String orderId;
//        private String paymentMode;
//        private Double maxAmount;
//        private String mandateId;
//        private Long customerId;
//        private Long customerSubId;
//        private String status;
//        private Long startDate;
//        private Long endDate;
//        private Long activatedAt;
//        private Long createdAt;
//        private String metaData;
//    }
//
//
//}
