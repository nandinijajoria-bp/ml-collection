package com.bharatpe.lending.entity;

import com.bharatpe.lending.common.entity.BaseEntity;
import lombok.Data;
import lombok.ToString;

import javax.persistence.*;

@Entity
@Table(name = "payment_banks")
@Data
@ToString
public class PaymentBanks extends BaseEntity {

    @Column(name = "bank_name", nullable = false)
    private String bankName;
    @Column(name = "loan_type", nullable = false)
    private String loanType;
    @Column(name = "status")
    private String status= "ACTIVE";
    @Column(name = "amount", nullable = false)
    private double amount;

}
