package com.bharatpe.lending.dto.payout;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author dhvl
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayoutResponseDTO {


    private String accountNumber;
    private String ifsc;
    private String orderId;
    private Long payoutId;
    private String payoutKey;
    private String payoutType;
    private String narration;
    private String beneficiaryName;
    private String gateway;
    private String mode;
    private BigDecimal amount;
    private String bankReferenceNo;
    private String status;
    private String internalResponseCode;
    private String remarks;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Kolkata")
    private LocalDateTime settlementDate;

}
