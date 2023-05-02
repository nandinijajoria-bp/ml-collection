package com.bharatpe.lending.dto;


import com.bharatpe.lending.enums.Lender;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Data
public class AutoPayUPIRegisterPgRequestDto {
    private String orderId;
    private Double orderAmount;
    private String redirectURI;
    private String redirectURIDeeplink;
    private String paymentPageHeaderText;
    private String narration;
    private String allowedModes;
    private Lender lender;
    private String checkout;
    private String orderType;
	private Long customerId;
	private Long customerSubId;
	private LocalDate mandateStartDate;
	private LocalDate mandateEndDate;

}
