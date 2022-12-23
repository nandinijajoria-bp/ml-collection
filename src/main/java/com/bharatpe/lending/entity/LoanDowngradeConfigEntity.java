package com.bharatpe.lending.entity;

import com.bharatpe.common.entities.BaseEntity;
import com.bharatpe.lending.common.enums.PincodeColor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@Entity
@Table(name = "loan_downgrade_config")
@NoArgsConstructor
public class LoanDowngradeConfigEntity extends BaseEntity {

    @Column(name = "risk_segment")
    String riskSegment;

    @Column(name = "risk_group")
    String riskGroup;

    @Enumerated(EnumType.STRING)
    PincodeColor color;

    Integer tenure;

    @Column(name = "nfi_multiplier")
    Double nfiMultiplier;

    @Column(name = "tpv_multiplier")
    Double tpvMultiplier;

    @Column(name = "nfi_sms_multiplier")
    Double nfiSmsMultiplier;

    Double roi;

    @Column(name = "max_limit_temp")
    Double maxLimitTemp;

    @Column(name = "max_limit_mov")
    Double maxLimitMov;

    @Column(name = "processing_fee")
    Double processingFee;

    @Column(name = "version")
    Double version;

}

