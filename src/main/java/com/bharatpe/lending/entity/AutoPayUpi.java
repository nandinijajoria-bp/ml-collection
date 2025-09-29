package com.bharatpe.lending.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import com.bharatpe.lending.common.entity.BaseEntity;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Column;
import java.sql.Timestamp;

@Entity
@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
@Table(name = "autopay_upi")
public class AutoPayUpi extends BaseEntity {

    @Column(name = "order_id", length = 50)
    private String orderId;

    @Column(name = "mandate_id", length = 100)
    private String mandateId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "lender", length = 50)
    private String lender;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "frequency")
    private Long frequency;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "status", length = 100)
    private String status;

    @Column(name = "payment_url_deeplink", length = 550)
    private String paymentUrlDeeplink;

    @Column(name = "gateway", length = 255)
    private String gateway;

    @Column(name = "error_code", length = 255)
    private String errorCode;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "mandate_end_date")
    private Timestamp mandateEndDate;

    @Column(name = "is_autopay_upi_deduction", length = 500, columnDefinition = "varchar(500) default 'AUTO_PAY_UPI'")
    private String isAutopayUpiDeduction;

    @Column(name = "is_standalone_autopay_setup", columnDefinition = "tinyint(1) default 0")
    private Boolean isStandaloneAutopaySetup;

}
