package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

@Entity
@Table(name = "autopay_upi_merchants")
@Data
public class AutoPayUPIMerchants extends BaseEntity {

    @Column(name = "merchant_id")
    private Long merchantId;
}