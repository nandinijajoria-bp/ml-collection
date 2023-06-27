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
@Table(name = "lending_refund_ledger")
public class LendingRefundLedger extends BaseEntity{

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "merchant_store_id")
    private Long merchantStoreId;

    @Column(name = "loan_id")
    private Long loanId;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "settlement_date")
    private Date settlementDate;

    @Column(name = "reference_no")
    private String referenceNo;

    @Column(name = "nach_lender")
    private String nachLender;

    @Column(name = "adjustment_mode")
    private String adjustmentMode;

    @Column(name = "terminal_order_id")
    private String terminalOrderId;

    @Column(name = "status")
    private String status;

}
