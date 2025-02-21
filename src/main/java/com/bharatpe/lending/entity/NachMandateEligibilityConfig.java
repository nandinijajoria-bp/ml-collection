package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Getter
@Setter
@Table(name = "nach_mandate_eligibility_config")
public class NachMandateEligibilityConfig extends BaseEntity{

    @Column(name = "lender")
    private String lender;

    @Column(name = "min_total_payable_amount")
    private Double minTotalPayableAmount;

    @Column(name = "max_total_payable_amount")
    private Double maxTotalPayableAmount;

    @Column(name = "upi_autopay_nach_required")
    private Boolean upiAutopayNachRequired;

    @Column(name = "upi_autopay_required")
    private Boolean upiAutopayRequired;

    @Column(name = "status")
    private Boolean status;

}

