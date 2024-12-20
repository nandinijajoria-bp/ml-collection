package com.bharatpe.lending.entity;


import com.bharatpe.lending.common.entity.BaseEntity;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Data
@Table(name = "user_otp_retry")
public class MerchantOtpRetry extends BaseEntity {
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "source")
    private String source;

    @Column(name = "mobile")
    private String mobile;

    @Column(name = "otp_tries")
    private int retries;

    @Column(name = "otp_verified")
    private boolean otpVerified;
}
