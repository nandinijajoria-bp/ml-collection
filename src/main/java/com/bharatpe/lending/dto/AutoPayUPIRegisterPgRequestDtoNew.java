package com.bharatpe.lending.dto;


import com.bharatpe.lending.enums.Lender;
import lombok.Data;

@Data
public class AutoPayUPIRegisterPgRequestDtoNew {
    private String orderId;
    private Double orderAmount;
    private String paymentPageHeaderText;
    private String redirectURIDeeplink;
    private String narration;
    private String checkout;
    private String isPgWebMode;
    private String redirectURI;
    private BankDetail bankDetail;
    private String orderType;
    private Long customerId;
    private Long mandateStartDate;
    private Long mandateEndDate;
    private Double maxMandateAmount;
    private Lender lender;
    private Long customerSubId;
}
