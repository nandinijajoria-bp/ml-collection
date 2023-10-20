package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Data
@JsonIgnoreProperties
@Table(name = "milestone_entity")
public class MileStoneEntity extends BaseEntity {

    @Column(name="sessionId")
    private String sessionId;

    @Column(name="merchant_id")
    private Long merchantId;

    @Column(name="response")
    private String response;

    @Column(name="expiryDate")
    private Date expiryDate;

    @Column(name="sessionStatus")
    private String sessionStatus;

    @Column(name="milestoneOffer")
    private Boolean milestoneOffer;

    @Column(name="programStartDate")
    private Date programStartDate;

    @Column(name="programDuration")
    private int programDuration;

    @Column(name="kycStatus")
    private String kycStatus;

}
