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
@Table(name = "autopay_upi_config")
public class AutopayUPIConfig extends BaseEntity {

    @Column(name = "lender")
    private String lender;

    @Column(name = "loan_segment")
    private String loanSegment;

    @Column(name = "status")
    private String status;

}
