package com.bharatpe.lending.entity;


import com.bharatpe.common.entities.BaseEntity;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "merchant_aggregate_data")
@Data
public class MerchantAggregateData extends BaseEntity {

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "application_type")
    private String applicationType;

    @Column(name = "sources")
    private String sources;

    @Column(name = "scienaptic_properties")
    private String scienapticProperties;

    @Column(name = "aggregate_id")
    private String aggregateId;

}
