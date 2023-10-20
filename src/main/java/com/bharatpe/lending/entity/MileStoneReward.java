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
@Table(name = "milestone_reward")
public class MileStoneReward extends BaseEntity {

    @Column(name="sessionId")
    private String sessionId;

    @Column(name="merchant_id")
    private Long merchantId;

    @Column(name="reward_name")
    private String rewardName;

    @Column(name = "reward_claim_status")
    private Boolean rewardClaimedStatus;

    @Column(name = "claimDate")
    private Date claimDate;

}
