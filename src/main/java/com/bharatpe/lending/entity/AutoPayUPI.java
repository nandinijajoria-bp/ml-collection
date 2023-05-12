package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import com.bharatpe.lending.constant.AutoPayStatusEnum;
import lombok.Data;

import javax.persistence.*;

@Entity
@Data
@Table(name = "autopay_upi")
public class AutoPayUPI extends BaseEntity {
    @Column(name="order_id")
    private String orderId;

    @Column(name="mandate_id")
    private String mandateId;

    @Column(name="application_id")
    private Long applicationId;

    @Column(name="lender")
    private String lender;

    @Column(name="merchant_id")
    private Long merchantId;

    private int frequency;

    private Double amount;

    @Column(name = "payment_url_deeplink")
    private String paymentURlDeepLink;

    @Enumerated(EnumType.STRING)
    private AutoPayStatusEnum status;
}
