package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import com.bharatpe.lending.constant.AutoPayStatusEnum;
import lombok.Data;

import javax.persistence.*;

@Entity
@Data
@Table(name = "autopay_upi")
public class AutoPayUPIEntity extends BaseEntity {
    private String orderId;
    private String mandateId;
    private Long applicationId;
    private String lender;
    private Long merchantId;
    private int frequency;
    private Double amount;
    @Column(name = "payment_url_deeplink")
    private String paymentURlDeepLink;

    @Enumerated(EnumType.STRING)
    private AutoPayStatusEnum status;
}
