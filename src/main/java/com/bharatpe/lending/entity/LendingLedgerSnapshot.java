package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "lending_ledger_snapshot")
public class LendingLedgerSnapshot extends BaseEntity {

    @Column(name = "source")
    private String source;

    @Column(name = "version")
    private Integer version;

    @Column(name = "ledger_id")
    private Long ledgerId;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "merchant_store_id")
    private Long merchantStoreId;

    @Column(name = "loan_id")
    private Long loanId;

    @Column(name = "default_settlement_id")
    private Long defaultSettlementId;

    @Column(name = "transaction_type")
    private String txnType;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "date")
    private Date date;

    @Column(name = "description")
    private String description;

    @Column(name = "settlement_id")
    private Long settlementId;

    @Column(name = "principle")
    private BigDecimal principle;

    @Column(name = "interest")
    private BigDecimal interest;

    @Column(name = "other_charges")
    private BigDecimal otherCharges;

    @Column(name = "penalty")
    private BigDecimal penalty;

    @Column(name = "adjustment_mode")
    private String adjustmentMode;

    @Column(name = "transfer_type")
    private String transferType;

    @Column(name = "terminal_order_id")
    private String terminalOrderId;
}
