package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.Date;

@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "lending_edi_schedule_snapshot")
public class LendingEdiScheduleSnapshot extends BaseEntity {

    @Column(name = "source")
    private String source;

    @Column(name = "version")
    private Integer version;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "merchant_store_id")
    private Long merchantStoreId;

    @Column(name = "loan_id")
    private Long loanId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "construct")
    private String construct;

    @Column(name = "date")
    private Date date;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "edi_type")
    private String ediType;

    @Column(name = "opening_balance")
    private Double openingBalance;

    @Column(name = "total_edi")
    private Integer totalEdi;

    @Column(name = "principle")
    private Double principle;

    @Column(name = "interest")
    private Double interest;

    @Column(name = "processing_fee")
    private Double processingFee;

    @Column(name = "other_charges")
    private Double otherCharges;

    @Column(name = "paid_principle")
    private Double paidPrinciple;

    @Column(name = "paid_interest")
    private Double paidInterest;
}
